package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Instantánea de una orden tras commit para emitir notificaciones WebSocket en tiempo real. */
public record DatosTiempoRealOrden(
        UUID ordenId,
        UUID tiendaId,
        String numeroOrden,
        String tokenAcceso,
        String nombreCliente,
        BigDecimal total,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion) {
}
