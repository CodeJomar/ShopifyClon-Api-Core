package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Orden bloqueada para una transición: sus tres estados, importes y líneas para inventario y balance. */
public record OrdenEstado(
        UUID id,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        BigDecimal total,
        PasarelaPago pasarela,
        Instant fechaExpiracionPago,
        UUID cuponId,
        List<LineaOrden> items,
        boolean requiereEnvio) {
}
