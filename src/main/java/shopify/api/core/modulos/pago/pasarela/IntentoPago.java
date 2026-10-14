package shopify.api.core.modulos.pago.pasarela;

/** Intento de cobro en la pasarela: su id, el secreto que usa el navegador para confirmarlo, y su estado. */
public record IntentoPago(String id, String clientSecret, String estado) {
}
