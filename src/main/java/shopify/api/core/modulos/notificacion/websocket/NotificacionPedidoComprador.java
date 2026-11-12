package shopify.api.core.modulos.notificacion.websocket;

import java.time.Instant;

/** Evento en tiempo real a nivel de pedido, emitido a {@code /topic/pedidos/{tokenAcceso}} (sin datos internos). */
public record NotificacionPedidoComprador(String tipo, String numeroPedido, String estado, Instant ocurridoEn) {
}
