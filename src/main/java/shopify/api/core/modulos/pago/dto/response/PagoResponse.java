package shopify.api.core.modulos.pago.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.modelo.TipoPago;

/** Un pago o reembolso de la orden. No incluye {@code DetallesRespuesta} (PAG-009). */
public record PagoResponse(
        UUID id,
        TipoPago tipo,
        PasarelaPago pasarela,
        EstadoRegistroPago estado,
        BigDecimal monto,
        String moneda,
        String referencia,
        String motivo,
        UUID pagoOrigenId,
        Instant fechaCreacion) {
}
