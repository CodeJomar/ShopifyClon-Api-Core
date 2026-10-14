package shopify.api.core.modulos.checkout.modelo;

import shopify.api.core.modulos.checkout.dto.response.CheckoutResponse;

/** Respuesta del checkout y si la orden se creó en esta llamada (201) o ya existía por idempotencia (200). */
public record ResultadoCheckout(CheckoutResponse respuesta, boolean nueva) {
}
