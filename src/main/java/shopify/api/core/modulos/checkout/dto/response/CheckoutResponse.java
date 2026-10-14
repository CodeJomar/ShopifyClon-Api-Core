package shopify.api.core.modulos.checkout.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/** Orden creada (o la que ya existía con esa clave). No incluye la comisión de la plataforma. */
public record CheckoutResponse(
        UUID ordenId,
        String numeroOrden,
        EstadoOrden estado,
        EstadoPago estadoPago,
        String moneda,
        List<ItemCheckoutResponse> items,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        PasarelaPago pasarela,
        String tokenAcceso,
        Instant fechaExpiracionPago) {

    public record ItemCheckoutResponse(
            String tituloProducto,
            String tituloVariante,
            String sku,
            BigDecimal precioUnitario,
            int cantidad,
            BigDecimal totalLinea) {
    }
}
