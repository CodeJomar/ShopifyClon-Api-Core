package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Pedido con sus órdenes hijas y su estado derivado. */
public record PedidoResumen(
        UUID id,
        String numeroPedido,
        String correoCliente,
        String nombreCliente,
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
        EstadoPedido estado,
        EstadoPagoPedido estadoPago,
        List<OrdenDePedido> ordenes) {
}
