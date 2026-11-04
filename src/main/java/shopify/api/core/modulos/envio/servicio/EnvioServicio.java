package shopify.api.core.modulos.envio.servicio;

import java.time.Clock;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.envio.dto.request.EntregaRequest;
import shopify.api.core.modulos.envio.dto.request.EnvioRequest;
import shopify.api.core.modulos.envio.dto.request.NoEntregadoRequest;
import shopify.api.core.modulos.envio.dto.response.EnvioResponse;
import shopify.api.core.modulos.envio.dto.response.EventoEnvioResponse;
import shopify.api.core.modulos.envio.entidad.EnvioEntidad;
import shopify.api.core.modulos.envio.entidad.EventoEnvioEntidad;
import shopify.api.core.modulos.envio.evento.EnvioDespachadoEvento;
import shopify.api.core.modulos.envio.evento.EnvioEntregadoEvento;
import shopify.api.core.modulos.envio.modelo.EnvioSeguimiento;
import shopify.api.core.modulos.envio.modelo.EstadoEnvio;
import shopify.api.core.modulos.envio.modelo.EstadoEventoEnvio;
import shopify.api.core.modulos.envio.repositorio.EnvioRepositorio;
import shopify.api.core.modulos.envio.repositorio.EventoEnvioRepositorio;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;

/**
 * Envíos (ENV-003 a ENV-006). El envío se bloquea después que la orden, en el mismo orden que el resto del sistema
 * (orden → envío), y todo ocurre en una transacción.
 */
@Service
@RequiredArgsConstructor
public class EnvioServicio {

    private static final String ALFABETO_GUIA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final DateTimeFormatter FECHA_GUIA = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final int INTENTOS_GUIA = 10;

    private final SecureRandom aleatorio = new SecureRandom();

    private final EnvioRepositorio envios;
    private final EventoEnvioRepositorio eventos;
    private final OrdenServicio ordenes;
    private final PublicadorEvento publicador;
    private final Clock reloj;

    /** ENV-003: prepara la orden. Pagada, sin preparar, con alguna línea que requiere envío y sin envío previo. */
    @Transactional
    public EnvioResponse crear(UUID tiendaId, UUID ordenId, EnvioRequest request) {
        OrdenEstado orden = ordenes.bloquear(tiendaId, ordenId);
        if (orden.estado() != EstadoOrden.ABIERTA || orden.estadoPago() != EstadoPago.PAGADO
                || orden.estadoPreparacion() != EstadoPreparacion.NO_PREPARADO || !orden.requiereEnvio()) {
            throw new ConflictoEstadoException("ENV-003", "Esta orden no está lista para preparar un envío");
        }
        if (envios.findFirstByOrdenIdAndEliminadoFalse(ordenId).isPresent()) {
            throw new ConflictoEstadoException("ENV-003", "Esta orden ya tiene un envío");
        }
        EnvioEntidad envio = new EnvioEntidad();
        envio.setOrdenId(ordenId);
        aplicar(envio, request);
        if (envio.getNumeroGuia() == null) {
            envio.setNumeroGuia(generarNumeroGuia());
        }
        envio.setEstado(EstadoEnvio.CREADO);
        envios.saveAndFlush(envio);
        registrarEvento(envio.getId(), EstadoEventoEnvio.CREADO, null, null);
        ordenes.marcarPreparada(ordenId);
        return respuesta(envio);
    }

    /** El envío de la orden. ORD-404 si la orden no es de la tienda; ENV-404 si la orden no tiene envío. */
    @Transactional(readOnly = true)
    public EnvioResponse obtenerDeOrden(UUID tiendaId, UUID ordenId) {
        ordenes.exigirDeTienda(tiendaId, ordenId);
        return envios.findFirstByOrdenIdAndEliminadoFalse(ordenId)
                .map(EnvioServicio::respuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("ENV-404", "No encontramos un envío para esta orden"));
    }

    /** ENV-006: edita transportista, guía, URL y fecha estimada mientras el envío no esté entregado. */
    @Transactional
    public EnvioResponse actualizar(UUID tiendaId, UUID envioId, EnvioRequest request) {
        EnvioEntidad envio = bloquearDeTienda(tiendaId, envioId);
        if (envio.getEstado() == EstadoEnvio.ENTREGADO) {
            throw new ConflictoEstadoException("ENV-006", "Un envío entregado no se puede editar");
        }
        aplicar(envio, request);
        envios.saveAndFlush(envio);
        return respuesta(envio);
    }

    /** ENV-004: CREADO → EN_TRANSITO con fecha de despacho. */
    @Transactional
    public EnvioResponse despachar(UUID tiendaId, UUID envioId) {
        EnvioEntidad envio = bloquearDeTienda(tiendaId, envioId);
        transicionar(envio, EstadoEnvio.EN_TRANSITO);
        envio.setFechaDespacho(Instant.now(reloj));
        envios.saveAndFlush(envio);
        registrarEvento(envioId, EstadoEventoEnvio.EN_TRANSITO, null, null);
        publicador.publicar(new EnvioDespachadoEvento(tiendaId, envioId, envio.getOrdenId()));
        return respuesta(envio);
    }

    /** ENV-004 y ENV-005: EN_TRANSITO → ENTREGADO; la orden pasa a ENTREGADO (y a CERRADA si ya está cobrada). */
    @Transactional
    public EnvioResponse entregar(UUID tiendaId, UUID envioId) {
        return entregar(tiendaId, envioId, null);
    }

    /** Igual que {@link #entregar(UUID, UUID)}, con foto de entrega (https) y nota opcionales que quedan en el evento. */
    @Transactional
    public EnvioResponse entregar(UUID tiendaId, UUID envioId, EntregaRequest datos) {
        String foto = datos == null ? null : vacioANulo(datos.fotoUrl());
        if (foto != null && !foto.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new ReglaNegocioException("VAL-001", "La URL de la foto debe comenzar con https://");
        }
        EnvioEntidad envio = bloquearDeTienda(tiendaId, envioId);
        transicionar(envio, EstadoEnvio.ENTREGADO);
        envio.setFechaEntregaReal(Instant.now(reloj));
        ordenes.marcarEntregada(envio.getOrdenId());
        envios.saveAndFlush(envio);
        registrarEvento(envioId, EstadoEventoEnvio.ENTREGADO, datos == null ? null : vacioANulo(datos.nota()), foto);
        publicador.publicar(new EnvioEntregadoEvento(tiendaId, envioId, envio.getOrdenId()));
        return respuesta(envio);
    }

    /** ENV-004: EN_TRANSITO → DEVUELTO. */
    @Transactional
    public EnvioResponse devolver(UUID tiendaId, UUID envioId) {
        EnvioEntidad envio = bloquearDeTienda(tiendaId, envioId);
        transicionar(envio, EstadoEnvio.DEVUELTO);
        envios.saveAndFlush(envio);
        registrarEvento(envioId, EstadoEventoEnvio.DEVUELTO, null, null);
        return respuesta(envio);
    }

    /**
     * Intento de entrega fallido (A-004): solo deja un evento {@code NO_ENTREGADO} con el motivo. El envío sigue
     * {@code EN_TRANSITO}; la tienda lo reintenta o lo devuelve. Fuera de ese estado, ENV-004.
     */
    @Transactional
    public EnvioResponse noEntregado(UUID tiendaId, UUID envioId, NoEntregadoRequest datos) {
        EnvioEntidad envio = bloquearDeTienda(tiendaId, envioId);
        if (envio.getEstado() != EstadoEnvio.EN_TRANSITO) {
            throw new ConflictoEstadoException("ENV-004", "Solo un envío en tránsito puede quedar como no entregado");
        }
        registrarEvento(envioId, EstadoEventoEnvio.NO_ENTREGADO, datos.nota().strip(), null);
        return respuesta(envio);
    }

    /** Historial del envío, el más reciente primero. ENV-404 si el envío no es de la tienda. */
    @Transactional(readOnly = true)
    public List<EventoEnvioResponse> eventos(UUID tiendaId, UUID envioId) {
        EnvioEntidad envio = envios.findFirstByIdAndEliminadoFalse(envioId).orElseThrow(EnvioServicio::envioNoEncontrado);
        try {
            ordenes.exigirDeTienda(tiendaId, envio.getOrdenId());
        } catch (RecursoNoEncontradoException ex) {
            throw envioNoEncontrado();
        }
        return eventos.findByEnvioIdAndEliminadoFalseOrderByFechaEventoDesc(envioId).stream()
                .map(e -> new EventoEnvioResponse(e.getId(), e.getEstado(), e.getNota(), e.getFotoUrl(), e.getFechaEvento()))
                .toList();
    }

    /**
     * Seguimiento público por número de guía: estado actual, transportista, fechas y eventos, sin datos del
     * comprador. Vacío si no existe; no distingue entre inexistente y de otra tienda. Si la guía manual se repite,
     * gana el envío más reciente.
     */
    @Transactional(readOnly = true)
    public Optional<EnvioSeguimiento> seguimiento(String numeroGuia) {
        if (numeroGuia == null || numeroGuia.isBlank()) {
            return Optional.empty();
        }
        return envios.findFirstByNumeroGuiaAndEliminadoFalseOrderByFechaCreacionDesc(numeroGuia.strip())
                .map(envio -> new EnvioSeguimiento(envio.getId(), envio.getNumeroGuia(), envio.getEstado(),
                        envio.getEmpresaTransporte(), envio.getFechaDespacho(), envio.getFechaEntregaEstimada(),
                        envio.getFechaEntregaReal(),
                        eventos.findByEnvioIdAndEliminadoFalseOrderByFechaEventoDesc(envio.getId()).stream()
                                .map(e -> new EventoEnvioResponse(e.getId(), e.getEstado(), e.getNota(), e.getFotoUrl(),
                                        e.getFechaEvento()))
                                .toList()));
    }

    /** Da número de guía al envío si no lo tiene (formato {@code OT-AAAAMMDD-XXXXXX}); respeta el manual. */
    @Transactional
    public String asegurarNumeroGuia(UUID envioId) {
        EnvioEntidad envio = envios.findFirstByIdAndEliminadoFalse(envioId).orElseThrow(EnvioServicio::envioNoEncontrado);
        if (envio.getNumeroGuia() == null) {
            envio.setNumeroGuia(generarNumeroGuia());
            envios.saveAndFlush(envio);
        }
        return envio.getNumeroGuia();
    }

    private String generarNumeroGuia() {
        for (int intento = 0; intento < INTENTOS_GUIA; intento++) {
            StringBuilder sufijo = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sufijo.append(ALFABETO_GUIA.charAt(aleatorio.nextInt(ALFABETO_GUIA.length())));
            }
            String guia = "OT-" + FECHA_GUIA.format(Instant.now(reloj)) + "-" + sufijo;
            if (!envios.existsByNumeroGuia(guia)) {
                return guia;
            }
        }
        throw new IllegalStateException("No se pudo generar un número de guía único");
    }

    private void registrarEvento(UUID envioId, EstadoEventoEnvio estado, String nota, String fotoUrl) {
        EventoEnvioEntidad evento = new EventoEnvioEntidad();
        evento.setEnvioId(envioId);
        evento.setEstado(estado);
        evento.setNota(nota);
        evento.setFotoUrl(fotoUrl);
        evento.setFechaEvento(Instant.now(reloj));
        eventos.save(evento);
    }

    /**
     * Envío de la tienda, bloqueado. Primero se bloquea la orden (orden → envío). Un envío de otra tienda responde
     * ENV-404; la excepción de la orden se traduce y se relanza, así la transacción no queda a medias.
     */
    private EnvioEntidad bloquearDeTienda(UUID tiendaId, UUID envioId) {
        EnvioEntidad envio = envios.findFirstByIdAndEliminadoFalse(envioId).orElseThrow(EnvioServicio::envioNoEncontrado);
        try {
            ordenes.bloquear(tiendaId, envio.getOrdenId());
        } catch (RecursoNoEncontradoException ex) {
            throw envioNoEncontrado();
        }
        return envios.bloquearPorId(envioId).orElseThrow(EnvioServicio::envioNoEncontrado);
    }

    private static void transicionar(EnvioEntidad envio, EstadoEnvio destino) {
        if (!envio.getEstado().puedeTransicionarA(destino)) {
            throw new ConflictoEstadoException("ENV-004",
                    "El envío no puede pasar a " + destino.name().toLowerCase(Locale.ROOT));
        }
        envio.setEstado(destino);
    }

    private static void aplicar(EnvioEntidad envio, EnvioRequest request) {
        String url = vacioANulo(request.urlSeguimiento());
        if (url != null && !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new ReglaNegocioException("VAL-001", "La URL de seguimiento debe comenzar con https://");
        }
        envio.setEmpresaTransporte(vacioANulo(request.empresaTransporte()));
        envio.setNumeroGuia(vacioANulo(request.numeroGuia()));
        envio.setUrlSeguimiento(url);
        envio.setFechaEntregaEstimada(request.fechaEntregaEstimada());
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }

    private static RecursoNoEncontradoException envioNoEncontrado() {
        return new RecursoNoEncontradoException("ENV-404", "No encontramos ese envío");
    }

    private static EnvioResponse respuesta(EnvioEntidad envio) {
        return new EnvioResponse(envio.getId(), envio.getOrdenId(), envio.getEmpresaTransporte(), envio.getNumeroGuia(),
                envio.getUrlSeguimiento(), envio.getEstado(), envio.getFechaDespacho(),
                envio.getFechaEntregaEstimada(), envio.getFechaEntregaReal());
    }
}
