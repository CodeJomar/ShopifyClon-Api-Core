package shopify.api.core.modulos.notificacion.websocket;

import java.time.Instant;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;

/**
 * Evento en tiempo real emitido al canal del comprador ({@code /topic/ordenes/{tokenAcceso}}).
 * Omite identificadores internos de tienda/orden, comisiones y datos personales de terceros.
 */
public record NotificacionOrdenComprador(
        String tipo,
        String numeroOrden,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        String motivo,
        String numeroGuia,
        String transportista,
        String urlSeguimiento,
        Instant ocurridoEn) {
}
