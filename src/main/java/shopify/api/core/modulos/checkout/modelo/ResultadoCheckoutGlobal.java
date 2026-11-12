package shopify.api.core.modulos.checkout.modelo;

import shopify.api.core.modulos.orden.dto.response.PedidoResponse;

/** Respuesta del checkout global y si el pedido se creó en esta llamada (201) o ya existía por idempotencia (200). */
public record ResultadoCheckoutGlobal(PedidoResponse pedido, boolean nuevo) {
}
