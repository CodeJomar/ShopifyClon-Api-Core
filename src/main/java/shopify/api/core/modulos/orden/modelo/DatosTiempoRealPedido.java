package shopify.api.core.modulos.orden.modelo;

/** Instantánea de un pedido tras commit para emitir su notificación en tiempo real. */
public record DatosTiempoRealPedido(String numeroPedido, String tokenAcceso, EstadoPedido estado) {
}
