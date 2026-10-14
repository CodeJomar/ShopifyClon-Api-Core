package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Orden guardada, con lo que necesita la respuesta del checkout. */
public record OrdenResumen(
        UUID id,
        String numeroOrden,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        PasarelaPago pasarela,
        String tokenAcceso,
        Instant fechaExpiracionPago,
        List<ItemOrdenResumen> items) {
}
