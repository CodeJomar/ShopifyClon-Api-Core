package shopify.api.core.modulos.orden.servicio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.modulos.orden.dto.response.ItemOrdenVistaResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenCuentaResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenDetallePanelResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenListaResponse;
import shopify.api.core.modulos.orden.dto.response.OrdenPublicaResponse;
import shopify.api.core.modulos.orden.entidad.ItemOrdenEntidad;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.repositorio.ItemOrdenRepositorio;
import shopify.api.core.modulos.orden.repositorio.OrdenConsultaRepositorio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/**
 * Consultas de órdenes (ORD-010 a ORD-012). Solo lectura: no escribe nada y no toca {@code OrdenServicio}.
 */
@Service
@RequiredArgsConstructor
public class OrdenConsultaServicio {

    static final int TAMANO_PAGINA = 20;
    private static final Instant DESDE_MINIMO = Instant.EPOCH;
    private static final Instant HASTA_MAXIMO = Instant.parse("9999-12-31T23:59:59Z");
    private static final UUID SIN_TIENDA = new UUID(0L, 0L);

    private final OrdenConsultaRepositorio ordenes;
    private final ItemOrdenRepositorio items;
    private final TiendaServicio tiendas;
    private final JdbcTemplate jdbc;

    /** ORD-011: listado del panel, más recientes primero, con filtros opcionales. */
    @Transactional(readOnly = true)
    public Page<OrdenListaResponse> listarPanel(UUID tiendaId, int pagina, String estado, String estadoPago,
                                                String preparacion, LocalDate desde, LocalDate hasta, String texto) {
        boolean conEstado = hayValor(estado);
        boolean conPago = hayValor(estadoPago);
        boolean conPreparacion = hayValor(preparacion);
        EstadoOrden filtroEstado = conEstado ? enumDe(EstadoOrden.class, estado, "estado") : EstadoOrden.ABIERTA;
        EstadoPago filtroPago = conPago ? enumDe(EstadoPago.class, estadoPago, "estado_pago") : EstadoPago.PENDIENTE;
        EstadoPreparacion filtroPreparacion = conPreparacion
                ? enumDe(EstadoPreparacion.class, preparacion, "estado_preparacion") : EstadoPreparacion.NO_PREPARADO;

        String moneda = monedaDe(tiendaId);
        Page<OrdenEntidad> resultado = ordenes.buscarEnTienda(tiendaId,
                conEstado, filtroEstado, conPago, filtroPago, conPreparacion, filtroPreparacion,
                inicioDelDia(desde, DESDE_MINIMO), finDelDia(hasta), patron(texto),
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaCreacion")));
        return resultado.map(orden -> new OrdenListaResponse(orden.getId(), orden.getNumeroOrden(),
                orden.getFechaCreacion(), orden.getNombreCliente(), orden.getCorreoCliente(), orden.getTotal(), moneda,
                orden.getEstado(), orden.getEstadoPago(), orden.getEstadoPreparacion()));
    }

    /** ORD-011: detalle completo para el panel. Otra tienda → ORD-404. */
    @Transactional(readOnly = true)
    public OrdenDetallePanelResponse detallePanel(UUID tiendaId, UUID ordenId) {
        OrdenEntidad o = ordenes.findFirstByIdAndTiendaIdAndEliminadoFalse(ordenId, tiendaId).orElseThrow(OrdenConsultaServicio::noEncontrada);
        return new OrdenDetallePanelResponse(o.getId(), o.getNumeroOrden(), o.getFechaCreacion(), o.getNombreCliente(),
                o.getCorreoCliente(), o.getTelefonoCliente(), o.getDireccionEnvio(), o.getDireccionFacturacion(),
                itemsDe(o.getId()), o.getSubtotal(), o.getDescuentoTotal(), o.getCostoEnvio(), o.getImpuestosTotal(),
                o.getTotal(), monedaDe(tiendaId), o.getCodigoCupon(), o.getNombreTarifaEnvio(), o.getTasaImpuesto(),
                o.getPreciosIncluyenImpuesto(), o.getPasarelaPago(), o.getEstado(), o.getEstadoPago(),
                o.getEstadoPreparacion(), o.getFechaExpiracionPago(), o.getFechaCancelacion(), o.getMotivoCancelacion(),
                o.getFechaCierre(), o.getComisionPlataforma());
    }

    /** ORD-012: historial del comprador; {@code tiendaId} nulo significa todas sus tiendas. Sin comisión. */
    @Transactional(readOnly = true)
    public Page<OrdenCuentaResponse> historialDelUsuario(UUID usuarioId, int pagina, UUID tiendaId) {
        Map<UUID, Map<String, Object>> cacheTiendas = new HashMap<>();
        return ordenes.historialDelUsuario(usuarioId, tiendaId != null, tiendaId == null ? SIN_TIENDA : tiendaId,
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA, Sort.by(Sort.Direction.DESC, "fechaCreacion")))
                .map(o -> {
                    Map<String, Object> tienda = cacheTiendas.computeIfAbsent(o.getTiendaId(), this::infoTienda);
                    return new OrdenCuentaResponse(o.getId(), o.getNumeroOrden(), o.getFechaCreacion(), o.getTiendaId(),
                            (String) tienda.get("Nombre"), (String) tienda.get("Subdominio"), o.getTotal(),
                            (String) tienda.get("Moneda"), o.getEstado(), o.getEstadoPago(), o.getEstadoPreparacion());
                });
    }

    /** ORD-012: detalle de una orden del comprador. Si no es suya → ORD-404. */
    @Transactional(readOnly = true)
    public OrdenPublicaResponse detalleDelUsuario(UUID usuarioId, UUID ordenId) {
        OrdenEntidad o = ordenes.findFirstByIdAndUsuarioIdAndEliminadoFalse(ordenId, usuarioId).orElseThrow(OrdenConsultaServicio::noEncontrada);
        return vista(o, monedaDe(o.getTiendaId()));
    }

    /** ORD-010: estado de la orden para el comprador invitado. Token o tienda incorrectos → ORD-404. */
    @Transactional(readOnly = true)
    public OrdenPublicaResponse porToken(String subdominio, String tokenAcceso) {
        UUID tiendaId = tiendas.idPorSubdominio(subdominio)
                .orElseThrow(() -> new RecursoNoEncontradoException("TIE-404", "Esta tienda no está disponible"));
        OrdenEntidad o = ordenes.findFirstByTokenAccesoAndTiendaIdAndEliminadoFalse(tokenAcceso, tiendaId)
                .orElseThrow(OrdenConsultaServicio::noEncontrada);
        return vista(o, monedaDe(tiendaId));
    }

    private OrdenPublicaResponse vista(OrdenEntidad o, String moneda) {
        return new OrdenPublicaResponse(o.getNumeroOrden(), o.getFechaCreacion(), o.getNombreCliente(), o.getEstado(),
                o.getEstadoPago(), o.getEstadoPreparacion(), itemsDe(o.getId()), o.getSubtotal(), o.getDescuentoTotal(),
                o.getCostoEnvio(), o.getImpuestosTotal(), o.getTotal(), moneda, o.getDireccionEnvio(),
                o.getDireccionFacturacion(), o.getFechaExpiracionPago(), o.getPasarelaPago());
    }

    private List<ItemOrdenVistaResponse> itemsDe(UUID ordenId) {
        return items.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId).stream()
                .map(OrdenConsultaServicio::item)
                .toList();
    }

    private static ItemOrdenVistaResponse item(ItemOrdenEntidad i) {
        return new ItemOrdenVistaResponse(i.getTituloProducto(), i.getTituloVariante(), i.getSku(), i.getPrecioUnitario(),
                i.getCantidad(), i.getTotalLinea());
    }

    private String monedaDe(UUID tiendaId) {
        return (String) infoTienda(tiendaId).get("Moneda");
    }

    private Map<String, Object> infoTienda(UUID tiendaId) {
        return jdbc.queryForMap("SELECT \"Nombre\", \"Subdominio\", \"Moneda\" FROM \"Tiendas\" WHERE \"Id\" = ?", tiendaId);
    }

    private static boolean hayValor(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** VAL-003: un filtro con valor fuera del catálogo responde 400, nunca se ignora en silencio. */
    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new SolicitudInvalidaException("VAL-003", "El filtro " + campo + " no es válido");
        }
    }

    private static Instant inicioDelDia(LocalDate dia, Instant porOmision) {
        return dia == null ? porOmision : dia.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant finDelDia(LocalDate dia) {
        return dia == null ? HASTA_MAXIMO : dia.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
    }

    /** Texto libre: contiene, sin distinguir mayúsculas; {@code %} y {@code _} se escapan con '!'. */
    static String patron(String texto) {
        if (!hayValor(texto)) {
            return "%";
        }
        String escapado = texto.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado + "%";
    }

    private static RecursoNoEncontradoException noEncontrada() {
        return new RecursoNoEncontradoException("ORD-404", "No encontramos esa orden");
    }
}
