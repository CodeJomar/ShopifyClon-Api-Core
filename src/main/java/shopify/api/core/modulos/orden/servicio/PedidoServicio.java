package shopify.api.core.modulos.orden.servicio;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;
import shopify.api.core.modulos.orden.entidad.PedidoEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoPagoPedido;
import shopify.api.core.modulos.orden.modelo.EstadoPedido;
import shopify.api.core.modulos.orden.modelo.ItemOrdenResumen;
import shopify.api.core.modulos.orden.modelo.NuevoPedido;
import shopify.api.core.modulos.orden.modelo.OrdenDePedido;
import shopify.api.core.modulos.orden.modelo.PedidoCreado;
import shopify.api.core.modulos.orden.modelo.PedidoResumen;
import shopify.api.core.modulos.orden.repositorio.ItemOrdenRepositorio;
import shopify.api.core.modulos.orden.repositorio.OrdenRepositorio;
import shopify.api.core.modulos.orden.repositorio.PedidoRepositorio;

/**
 * Dueño de {@code Pedidos} (R-001, B5): la compra del comprador, con una orden hija por tienda. Crea el pedido, liga
 * sus órdenes y compone su estado a partir de ellas; las transiciones de cada orden siguen en {@link OrdenServicio}.
 */
@Service
@RequiredArgsConstructor
public class PedidoServicio {

    static final int TAMANO_PAGINA = 20;

    private final PedidoRepositorio pedidos;
    private final OrdenRepositorio ordenes;
    private final ItemOrdenRepositorio items;

    /** Inserta el pedido y liga sus órdenes hijas, en la transacción de quien llama. */
    @Transactional(propagation = Propagation.MANDATORY)
    public PedidoCreado crear(NuevoPedido datos, List<UUID> ordenIds) {
        PedidoEntidad pedido = new PedidoEntidad();
        pedido.setUsuarioId(datos.usuarioId());
        pedido.setNumeroPedido("P-" + String.format("%06d", pedidos.siguienteNumero()));
        pedido.setCorreoCliente(datos.correoCliente().trim().toLowerCase(Locale.ROOT));
        pedido.setNombreCliente(datos.nombreCliente());
        pedido.setTelefonoCliente(datos.telefonoCliente());
        pedido.setMoneda(datos.moneda());
        pedido.setSubtotal(datos.subtotal());
        pedido.setDescuentoTotal(datos.descuentoTotal());
        pedido.setCostoEnvio(datos.costoEnvio());
        pedido.setImpuestosTotal(datos.impuestosTotal());
        pedido.setTotal(datos.total());
        pedido.setPasarelaPago(datos.pasarela());
        pedido.setClaveIdempotencia(datos.claveIdempotencia());
        pedido.setTokenAcceso(datos.tokenAcceso());
        pedido.setFechaExpiracionPago(datos.fechaExpiracionPago());
        pedidos.saveAndFlush(pedido);

        for (UUID ordenId : ordenIds) {
            OrdenEntidad orden = ordenes.findFirstByIdAndEliminadoFalse(ordenId)
                    .orElseThrow(() -> new IllegalStateException("La orden " + ordenId + " debía existir"));
            orden.setPedidoId(pedido.getId());
            ordenes.saveAndFlush(orden);
        }
        return new PedidoCreado(pedido.getId(), pedido.getNumeroPedido());
    }

    @Transactional(readOnly = true)
    public Optional<PedidoResumen> buscarPorClave(UUID claveIdempotencia) {
        return pedidos.findFirstByClaveIdempotenciaAndEliminadoFalse(claveIdempotencia).map(this::resumen);
    }

    @Transactional(readOnly = true)
    public Optional<PedidoResumen> buscarPorId(UUID pedidoId) {
        return pedidos.findFirstByIdAndEliminadoFalse(pedidoId).map(this::resumen);
    }

    @Transactional(readOnly = true)
    public boolean existePorToken(String tokenAcceso) {
        return pedidos.findFirstByTokenAccesoAndEliminadoFalse(tokenAcceso).isPresent();
    }

    /** PED-404: el token no existe. Vista pública del invitado. */
    @Transactional(readOnly = true)
    public PedidoResumen porToken(String tokenAcceso) {
        return pedidos.findFirstByTokenAccesoAndEliminadoFalse(tokenAcceso == null ? "" : tokenAcceso)
                .map(this::resumen).orElseThrow(PedidoServicio::noEncontrado);
    }

    /** PED-404 también si el pedido es de otra persona. */
    @Transactional(readOnly = true)
    public PedidoResumen deUsuario(UUID usuarioId, UUID pedidoId) {
        return pedidos.findFirstByIdAndUsuarioIdAndEliminadoFalse(pedidoId, usuarioId)
                .map(this::resumen).orElseThrow(PedidoServicio::noEncontrado);
    }

    @Transactional(readOnly = true)
    public Page<PedidoResumen> historial(UUID usuarioId, int pagina) {
        Page<PedidoEntidad> pagina0 = pedidos.findByUsuarioIdAndEliminadoFalseOrderByFechaCreacionDesc(usuarioId,
                PageRequest.of(Math.max(pagina, 0), TAMANO_PAGINA));
        List<UUID> ids = pagina0.getContent().stream().map(PedidoEntidad::getId).toList();
        Map<UUID, List<OrdenEntidad>> hijas = ids.isEmpty() ? Map.of()
                : ordenes.findByPedidoIdInAndEliminadoFalseOrderByFechaCreacionAscIdAsc(ids).stream()
                        .collect(Collectors.groupingBy(OrdenEntidad::getPedidoId));
        Map<UUID, List<ItemOrdenResumen>> lineas = lineasDe(hijas.values().stream().flatMap(List::stream).toList());
        return pagina0.map(pedido -> resumen(pedido, hijas.getOrDefault(pedido.getId(), List.of()), lineas));
    }

    private PedidoResumen resumen(PedidoEntidad pedido) {
        List<OrdenEntidad> hijas = ordenes.findByPedidoIdAndEliminadoFalseOrderByFechaCreacionAscIdAsc(pedido.getId());
        return resumen(pedido, hijas, lineasDe(hijas));
    }

    private PedidoResumen resumen(PedidoEntidad pedido, List<OrdenEntidad> hijas, Map<UUID, List<ItemOrdenResumen>> lineas) {
        List<OrdenDePedido> ordenesDto = hijas.stream().map(o -> aOrden(o, lineas.getOrDefault(o.getId(), List.of()))).toList();
        return new PedidoResumen(pedido.getId(), pedido.getNumeroPedido(), pedido.getCorreoCliente(),
                pedido.getNombreCliente(), pedido.getMoneda(), pedido.getSubtotal(), pedido.getDescuentoTotal(),
                pedido.getCostoEnvio(), pedido.getImpuestosTotal(), pedido.getTotal(), pedido.getPasarelaPago(),
                pedido.getTokenAcceso(), pedido.getFechaCreacion(), pedido.getFechaExpiracionPago(),
                EstadoPedido.derivar(ordenesDto), EstadoPagoPedido.derivar(ordenesDto), ordenesDto);
    }

    /** Líneas de todas las órdenes dadas en una sola consulta, agrupadas por orden. */
    private Map<UUID, List<ItemOrdenResumen>> lineasDe(List<OrdenEntidad> hijas) {
        if (hijas.isEmpty()) {
            return Map.of();
        }
        return items.findByOrdenIdInAndEliminadoFalseOrderByFechaCreacionAsc(hijas.stream().map(OrdenEntidad::getId).toList())
                .stream().collect(Collectors.groupingBy(item -> item.getOrdenId(), java.util.LinkedHashMap::new,
                        Collectors.mapping(item -> new ItemOrdenResumen(item.getTituloProducto(), item.getTituloVariante(),
                                item.getSku(), item.getPrecioUnitario(), item.getCantidad(), item.getTotalLinea()),
                                Collectors.toList())));
    }

    private OrdenDePedido aOrden(OrdenEntidad orden, List<ItemOrdenResumen> lineas) {
        return new OrdenDePedido(orden.getId(), orden.getNumeroOrden(), orden.getTiendaId(), orden.getEstado(),
                orden.getEstadoPago(), orden.getEstadoPreparacion(), orden.getTotal(), orden.getTokenAcceso(), lineas);
    }

    private static RecursoNoEncontradoException noEncontrado() {
        return new RecursoNoEncontradoException("PED-404", "No encontramos ese pedido");
    }
}
