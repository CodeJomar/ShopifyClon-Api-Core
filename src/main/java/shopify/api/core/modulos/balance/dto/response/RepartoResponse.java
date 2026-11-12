package shopify.api.core.modulos.balance.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Asiento del libro de repartos: pedido, orden, tienda, bruto, comisión y neto. */
public record RepartoResponse(
        UUID id,
        UUID pedidoId,
        UUID ordenId,
        UUID tiendaId,
        String tipo,
        BigDecimal bruto,
        BigDecimal comision,
        BigDecimal neto,
        String moneda,
        Instant fecha) {
}
