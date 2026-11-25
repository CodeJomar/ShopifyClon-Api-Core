package shopify.api.core.modulos.orden.servicio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modulos.orden.dto.response.ItemOrdenVistaResponse;
import shopify.api.core.modulos.orden.dto.response.PedidoResponse;
import shopify.api.core.modulos.orden.dto.response.PedidoResponse.OrdenDelPedidoResponse;
import shopify.api.core.modulos.orden.dto.response.PedidoResponse.TiendaDelPedido;
import shopify.api.core.modulos.orden.modelo.OrdenDePedido;
import shopify.api.core.modulos.orden.modelo.PedidoResumen;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/** Lectura de pedidos para el comprador (R-001, B5): por token (invitado) o por sesión. Solo consulta. */
@Service
@RequiredArgsConstructor
public class PedidoConsultaServicio {

    private final PedidoServicio pedidos;
    private final TiendaServicio tiendas;

    @Transactional(readOnly = true)
    public PedidoResponse porToken(String tokenAcceso) {
        return aRespuesta(pedidos.porToken(tokenAcceso), new HashMap<>());
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalleDelUsuario(UUID usuarioId, UUID pedidoId) {
        return aRespuesta(pedidos.deUsuario(usuarioId, pedidoId), new HashMap<>());
    }

    @Transactional(readOnly = true)
    public Page<PedidoResponse> historialDelUsuario(UUID usuarioId, int pagina) {
        Page<PedidoResumen> resumenes = pedidos.historial(usuarioId, pagina);
        // Las tiendas de toda la página en una sola consulta, en vez de una por orden
        Map<UUID, TiendaDelPedido> cache = new HashMap<>();
        tiendas.publicas(resumenes.getContent().stream().flatMap(p -> p.ordenes().stream()).map(OrdenDePedido::tiendaId)
                .collect(java.util.stream.Collectors.toSet()))
                .forEach((id, t) -> cache.put(id, new TiendaDelPedido(t.id(), t.nombre(), t.subdominio())));
        return resumenes.map(pedido -> aRespuesta(pedido, cache));
    }

    /** Respuesta de un pedido ya leído (también la usa el checkout global). */
    @Transactional(readOnly = true)
    public PedidoResponse aRespuesta(PedidoResumen pedido) {
        return aRespuesta(pedido, new HashMap<>());
    }

    private PedidoResponse aRespuesta(PedidoResumen pedido, Map<UUID, TiendaDelPedido> cache) {
        List<OrdenDelPedidoResponse> ordenes = pedido.ordenes().stream().map(o -> aOrden(o, cache)).toList();
        return new PedidoResponse(pedido.id(), pedido.numeroPedido(), pedido.estado(), pedido.estadoPago(),
                pedido.moneda(), pedido.subtotal(), pedido.descuentoTotal(), pedido.costoEnvio(),
                pedido.impuestosTotal(), pedido.total(), pedido.pasarela(), pedido.tokenAcceso(),
                pedido.fechaCreacion(), pedido.fechaExpiracionPago(), ordenes);
    }

    private OrdenDelPedidoResponse aOrden(OrdenDePedido orden, Map<UUID, TiendaDelPedido> cache) {
        TiendaDelPedido tienda = cache.computeIfAbsent(orden.tiendaId(), id -> tiendas.publica(id)
                .map(t -> new TiendaDelPedido(t.id(), t.nombre(), t.subdominio()))
                .orElse(new TiendaDelPedido(id, null, null)));
        List<ItemOrdenVistaResponse> items = orden.items().stream()
                .map(i -> new ItemOrdenVistaResponse(i.tituloProducto(), i.tituloVariante(), i.sku(), i.precioUnitario(),
                        i.cantidad(), i.totalLinea()))
                .toList();
        return new OrdenDelPedidoResponse(orden.ordenId(), orden.numeroOrden(), tienda, orden.estado(), orden.estadoPago(),
                orden.estadoPreparacion(), orden.total(), orden.tokenAcceso(), items);
    }
}
