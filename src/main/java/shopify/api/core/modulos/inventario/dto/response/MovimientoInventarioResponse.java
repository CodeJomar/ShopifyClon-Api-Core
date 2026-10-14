package shopify.api.core.modulos.inventario.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Un movimiento del libro de inventario (solo lectura). */
public record MovimientoInventarioResponse(
        UUID id,
        String tipo,
        int deltaDisponible,
        int deltaReservado,
        int disponibleResultante,
        int reservadoResultante,
        UUID ordenId,
        String motivo,
        Instant fecha) {
}
