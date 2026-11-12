package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPagoPedido;
import shopify.api.core.modulos.orden.modelo.EstadoPedido;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/**
 * Pedido del comprador (R-001, B5): sus totales, su estado derivado y una sección por orden hija (una por tienda).
 * {@code tokenAcceso} de cada orden sirve para su vista pública y su seguimiento.
 */
public record PedidoResponse(
        UUID pedidoId,
        String numeroPedido,
        EstadoPedido estado,
        EstadoPagoPedido estadoPago,
        String moneda,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        PasarelaPago pasarela,
        String tokenAcceso,
        Instant fechaCreacion,
        Instant fechaExpiracionPago,
        List<OrdenDelPedidoResponse> ordenes) {

    public record TiendaDelPedido(UUID id, String nombre, String subdominio) {
    }

    public record OrdenDelPedidoResponse(
            UUID ordenId,
            String numeroOrden,
            TiendaDelPedido tienda,
            EstadoOrden estado,
            EstadoPago estadoPago,
            EstadoPreparacion estadoPreparacion,
            BigDecimal total,
            String tokenAcceso,
            List<ItemOrdenVistaResponse> items) {
    }
}
