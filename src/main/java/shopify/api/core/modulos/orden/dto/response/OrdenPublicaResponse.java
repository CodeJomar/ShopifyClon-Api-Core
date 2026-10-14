package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/** Estado de la orden para el comprador invitado, por su token (ORD-010). Sin comisión ni datos internos. */
public record OrdenPublicaResponse(
        String numeroOrden,
        Instant fechaCreacion,
        String nombreCliente,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        List<ItemOrdenVistaResponse> items,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        String moneda,
        Map<String, Object> direccionEnvio,
        Map<String, Object> direccionFacturacion,
        Instant fechaExpiracionPago,
        PasarelaPago pasarela) {
}
