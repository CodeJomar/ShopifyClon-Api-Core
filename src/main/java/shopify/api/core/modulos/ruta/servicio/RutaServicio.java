package shopify.api.core.modulos.ruta.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.envio.servicio.EnvioServicio;
import shopify.api.core.modulos.ruta.dto.request.RutaRequest;
import shopify.api.core.modulos.ruta.dto.response.EnvioDisponibleResponse;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse.Direccion;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse.Item;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse.Parada;
import shopify.api.core.modulos.ruta.dto.response.RutaDetalleResponse.Remitente;
import shopify.api.core.modulos.ruta.dto.response.RutaListaResponse;
import shopify.api.core.modulos.ruta.entidad.ParadaRutaEntidad;
import shopify.api.core.modulos.ruta.entidad.RutaEntidad;
import shopify.api.core.modulos.ruta.modelo.ConteoRutaVista;
import shopify.api.core.modulos.ruta.modelo.EnvioParaRutaVista;
import shopify.api.core.modulos.ruta.modelo.EstadoRuta;
import shopify.api.core.modulos.ruta.modelo.ItemParadaVista;
import shopify.api.core.modulos.ruta.modelo.ParadaVista;
import shopify.api.core.modulos.ruta.modelo.RemitenteVista;
import shopify.api.core.modulos.ruta.repositorio.ParadaRutaRepositorio;
import shopify.api.core.modulos.ruta.repositorio.RutaRepositorio;

/**
 * Hojas de ruta de reparto (RUT-001 a RUT-005). Los envíos cambian de estado solo a través de {@link EnvioServicio}:
 * iniciar la ruta despacha los envíos creados con su evento y su fecha de despacho.
 */
@Service
@RequiredArgsConstructor
public class RutaServicio {

    static final int TAMANO_PAGINA = 20;
    private static final UUID SIN_RUTA = new UUID(0L, 0L);
    private static final LocalDate DESDE = LocalDate.of(1900, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(9999, 12, 31);

    private final RutaRepositorio rutas;
    private final ParadaRutaRepositorio paradas;
    private final EnvioServicio envios;
    private final ObjectMapper json;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public Page<RutaListaResponse> listar(ContextoTienda contexto, int pagina, EstadoRuta estado, LocalDate fecha) {
        Set<EstadoRuta> estados = estado == null ? EnumSet.allOf(EstadoRuta.class) : EnumSet.of(estado);
        Page<RutaEntidad> resultado = rutas.buscar(contexto.tiendaId(), estados,
                fecha == null ? DESDE : fecha, fecha == null ? HASTA : fecha,
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA,
                        Sort.by(Sort.Direction.DESC, "fecha").and(Sort.by(Sort.Direction.DESC, "fechaCreacion"))));

        List<UUID> ids = resultado.getContent().stream().map(RutaEntidad::getId).toList();
        Map<UUID, ConteoRutaVista> conteos = ids.isEmpty() ? Map.of()
                : rutas.conteosDe(ids).stream().collect(Collectors.toMap(ConteoRutaVista::getRutaId, Function.identity()));
        return resultado.map(ruta -> {
            ConteoRutaVista conteo = conteos.get(ruta.getId());
            return new RutaListaResponse(ruta.getId(), ruta.getCodigo(), ruta.getFecha(), ruta.getChofer(), ruta.getPlaca(),
                    ruta.getEstado(), conteo == null ? 0 : conteo.getTotal(), conteo == null ? 0 : conteo.getEntregadas());
        });
    }

    @Transactional(readOnly = true)
    public RutaDetalleResponse obtener(ContextoTienda contexto, UUID id) {
        return detalle(buscar(contexto, id));
    }

    /** Chofer de la ruta más reciente del envío; lo usa el seguimiento público. */
    @Transactional(readOnly = true)
    public java.util.Optional<String> choferDe(UUID envioId) {
        return rutas.choferDe(envioId);
    }

    @Transactional(readOnly = true)
    public List<EnvioDisponibleResponse> disponibles(ContextoTienda contexto) {
        return rutas.disponiblesParaRuta(contexto.tiendaId()).stream()
                .map(v -> new EnvioDisponibleResponse(v.getEnvioId(), v.getNumeroOrden(), v.getNumeroGuia(),
                        v.getEstadoEnvio(), v.getDestinatario(), v.getCiudad(), v.getDireccionLinea1(),
                        v.getTotalItems() == null ? 0 : v.getTotalItems()))
                .toList();
    }

    /** RUT-001 y RUT-002: la ruta nace en BORRADOR con las paradas en el orden recibido. */
    @Transactional
    public RutaDetalleResponse crear(ContextoTienda contexto, RutaRequest request) {
        UUID tiendaId = contexto.tiendaId();
        List<UUID> ids = sinRepetidos(request.envioIds());
        exigirFechaNoPasada(tiendaId, request.fecha());

        rutas.bloquearTienda(tiendaId);
        validarEnvios(tiendaId, ids, SIN_RUTA);

        RutaEntidad ruta = new RutaEntidad();
        ruta.setTiendaId(tiendaId);
        ruta.setCodigo(String.format("RT-%04d", rutas.contarDeTienda(tiendaId) + 1));
        aplicarDatos(ruta, request);
        ruta.setEstado(EstadoRuta.BORRADOR);
        rutas.saveAndFlush(ruta);

        guardarParadas(ruta.getId(), ids);
        return detalle(ruta);
    }

    /** RUT-005: solo una ruta en BORRADOR se edita (datos y paradas, incluido el orden). */
    @Transactional
    public RutaDetalleResponse actualizar(ContextoTienda contexto, UUID id, RutaRequest request) {
        UUID tiendaId = contexto.tiendaId();
        RutaEntidad ruta = bloquear(contexto, id);
        if (ruta.getEstado() != EstadoRuta.BORRADOR) {
            throw new ConflictoEstadoException("RUT-005", "Solo se puede editar una ruta en borrador");
        }
        List<UUID> ids = sinRepetidos(request.envioIds());
        if (!request.fecha().equals(ruta.getFecha())) {
            exigirFechaNoPasada(tiendaId, request.fecha());
        }

        rutas.bloquearTienda(tiendaId);
        validarEnvios(tiendaId, ids, ruta.getId());

        aplicarDatos(ruta, request);
        rutas.saveAndFlush(ruta);

        List<ParadaRutaEntidad> anteriores = paradas.findByRutaIdAndEliminadoFalseOrderByPosicion(ruta.getId());
        anteriores.forEach(parada -> parada.setEliminado(true));
        paradas.saveAllAndFlush(anteriores);
        guardarParadas(ruta.getId(), ids);
        return detalle(ruta);
    }

    /** RUT-003: BORRADOR → EN_CURSO; los envíos creados pasan a EN_TRANSITO con su evento y fecha de despacho. */
    @Transactional
    public RutaDetalleResponse iniciar(ContextoTienda contexto, UUID id) {
        RutaEntidad ruta = bloquear(contexto, id);
        if (ruta.getEstado() != EstadoRuta.BORRADOR) {
            throw new ConflictoEstadoException("RUT-003", "Solo una ruta en borrador se puede iniciar");
        }
        List<ParadaVista> lista = rutas.paradasDe(ruta.getId());
        for (ParadaVista parada : lista) {
            if ("CREADO".equals(parada.getEstadoEnvio())) {
                envios.despachar(contexto.tiendaId(), parada.getEnvioId());
            }
        }
        ruta.setEstado(EstadoRuta.EN_CURSO);
        rutas.saveAndFlush(ruta);
        return detalle(ruta);
    }

    /** RUT-004: EN_CURSO → FINALIZADA, aun con paradas pendientes (quedan como están). */
    @Transactional
    public RutaDetalleResponse finalizar(ContextoTienda contexto, UUID id) {
        RutaEntidad ruta = bloquear(contexto, id);
        if (ruta.getEstado() != EstadoRuta.EN_CURSO) {
            throw new ConflictoEstadoException("RUT-004", "Solo una ruta en curso se puede finalizar");
        }
        ruta.setEstado(EstadoRuta.FINALIZADA);
        rutas.saveAndFlush(ruta);
        return detalle(ruta);
    }

    // ---- validaciones ----

    /** RUT-001 (misma tienda y estado CREADO o EN_TRANSITO) y RUT-002 (una sola ruta activa por envío). */
    private void validarEnvios(UUID tiendaId, List<UUID> ids, UUID rutaActual) {
        Map<UUID, EnvioParaRutaVista> encontrados = rutas.enviosPorIds(ids).stream()
                .collect(Collectors.toMap(EnvioParaRutaVista::getEnvioId, Function.identity()));
        for (UUID id : ids) {
            EnvioParaRutaVista envio = encontrados.get(id);
            if (envio == null || !tiendaId.equals(envio.getTiendaId())) {
                // Un envío de otra tienda se trata como inexistente: no se revela
                throw new RecursoNoEncontradoException("ENV-404", "No encontramos uno de los envíos");
            }
            if (!"CREADO".equals(envio.getEstado()) && !"EN_TRANSITO".equals(envio.getEstado())) {
                throw new ReglaNegocioException("RUT-001", "Solo se pueden agregar envíos creados o en tránsito");
            }
        }
        List<UUID> repetidos = rutas.enviosEnRutaActiva(ids, rutaActual);
        if (!repetidos.isEmpty()) {
            throw new ConflictoEstadoException("RUT-002", "Un envío ya está en otra ruta que no ha finalizado",
                    Map.of("envio_ids", repetidos.stream().map(UUID::toString).toList()));
        }
        ids.forEach(envios::asegurarNumeroGuia);
    }

    private void exigirFechaNoPasada(UUID tiendaId, LocalDate fecha) {
        ZoneId zona;
        try {
            zona = ZoneId.of(rutas.zonaHorariaDe(tiendaId).orElse("UTC"));
        } catch (DateTimeException ex) {
            zona = ZoneId.of("UTC");
        }
        if (fecha.isBefore(LocalDate.now(reloj.withZone(zona)))) {
            throw new ReglaNegocioException("VAL-001", "La fecha de la ruta no puede ser anterior a hoy");
        }
    }

    private static List<UUID> sinRepetidos(List<UUID> ids) {
        Set<UUID> unicos = new LinkedHashSet<>(ids);
        if (unicos.size() != ids.size()) {
            throw new ReglaNegocioException("VAL-001", "Un envío no puede repetirse en la misma ruta");
        }
        return List.copyOf(unicos);
    }

    // ---- armado ----

    private void aplicarDatos(RutaEntidad ruta, RutaRequest request) {
        ruta.setFecha(request.fecha());
        ruta.setChofer(request.chofer().strip());
        ruta.setPlaca(request.placa().strip().toUpperCase());
        ruta.setNota(request.nota() == null || request.nota().isBlank() ? null : request.nota().strip());
    }

    private void guardarParadas(UUID rutaId, List<UUID> ids) {
        List<ParadaRutaEntidad> nuevas = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            ParadaRutaEntidad parada = new ParadaRutaEntidad();
            parada.setRutaId(rutaId);
            parada.setEnvioId(ids.get(i));
            parada.setPosicion(i + 1);
            nuevas.add(parada);
        }
        paradas.saveAllAndFlush(nuevas);
    }

    private RutaEntidad buscar(ContextoTienda contexto, UUID id) {
        return rutas.findByIdAndTiendaIdAndEliminadoFalse(id, contexto.tiendaId()).orElseThrow(RutaServicio::noEncontrada);
    }

    private RutaEntidad bloquear(ContextoTienda contexto, UUID id) {
        return rutas.bloquearPorIdYTienda(id, contexto.tiendaId()).orElseThrow(RutaServicio::noEncontrada);
    }

    private static RecursoNoEncontradoException noEncontrada() {
        return new RecursoNoEncontradoException("RUT-404", "No encontramos esa ruta");
    }

    private RutaDetalleResponse detalle(RutaEntidad ruta) {
        RemitenteVista remitente = rutas.remitenteDe(ruta.getTiendaId()).orElse(null);
        List<ParadaVista> vistas = rutas.paradasDe(ruta.getId());

        List<UUID> ordenIds = vistas.stream().map(ParadaVista::getOrdenId).distinct().toList();
        Map<UUID, List<Item>> itemsPorOrden = new HashMap<>();
        if (!ordenIds.isEmpty()) {
            for (ItemParadaVista item : rutas.itemsDe(ordenIds)) {
                itemsPorOrden.computeIfAbsent(item.getOrdenId(), k -> new ArrayList<>()).add(new Item(
                        item.getTituloProducto(), item.getTituloVariante(), item.getSku(),
                        item.getCantidad() == null ? 0 : item.getCantidad()));
            }
        }

        List<Parada> lista = vistas.stream().map(v -> parada(v, itemsPorOrden.getOrDefault(v.getOrdenId(), List.of()))).toList();
        Remitente tienda = new Remitente(remitente == null ? null : remitente.getNombre(),
                remitente == null ? null : remitente.getCorreo(), null, null);
        return new RutaDetalleResponse(ruta.getId(), ruta.getCodigo(), ruta.getFecha(), ruta.getChofer(), ruta.getPlaca(),
                ruta.getEstado(), ruta.getNota(), tienda, lista);
    }

    /** El destinatario y la dirección salen del snapshot que se congeló en la orden. */
    private Parada parada(ParadaVista vista, List<Item> items) {
        JsonNode snapshot = leer(vista.getDireccion());
        String destinatario = texto(snapshot, "nombre_contacto");
        String telefono = texto(snapshot, "telefono");
        Direccion direccion = new Direccion(texto(snapshot, "direccion_linea1"), texto(snapshot, "direccion_linea2"),
                texto(snapshot, "ciudad"), texto(snapshot, "estado_provincia"), texto(snapshot, "codigo_postal"),
                texto(snapshot, "pais"));
        return new Parada(vista.getPosicion(), vista.getEnvioId(), vista.getOrdenId(), vista.getNumeroOrden(),
                vista.getNumeroGuia(), vista.getEstadoEnvio(),
                destinatario != null ? destinatario : vista.getNombreCliente(),
                telefono != null ? telefono : vista.getTelefonoCliente(), direccion,
                decimal(snapshot, "latitud"), decimal(snapshot, "longitud"), items);
    }

    private JsonNode leer(String texto) {
        if (texto == null || texto.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(texto);
        } catch (java.io.IOException ex) {
            return json.createObjectNode();
        }
    }

    private static String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() || valor.asText().isBlank() ? null : valor.asText();
    }

    private static BigDecimal decimal(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() || !valor.isNumber() ? null : valor.decimalValue();
    }
}
