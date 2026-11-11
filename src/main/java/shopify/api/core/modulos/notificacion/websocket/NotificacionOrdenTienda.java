package shopify.api.core.modulos.notificacion.websocket;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;

/** Evento en tiempo real emitido al canal del panel de una tienda ({@code /topic/tienda/{tiendaId}/ordenes}). */
public record NotificacionOrdenTienda(
        String tipo,
        UUID ordenId,
        UUID tiendaId,
        String numeroOrden,
        String nombreCliente,
        BigDecimal total,
        String moneda,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        String motivo,
        UUID envioId,
        String numeroGuia,
        String transportista,
        Instant ocurridoEn) {
}
