package shopify.api.core.modulos.orden.eventos;

import java.util.UUID;

/** Publicada cuando un cobro único confirma las órdenes hijas de un pedido; avisa al comprador a nivel de pedido. */
public record PedidoPagadoEvento(UUID pedidoId) {
}
