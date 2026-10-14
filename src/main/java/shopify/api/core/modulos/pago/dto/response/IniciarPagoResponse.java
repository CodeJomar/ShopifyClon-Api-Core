package shopify.api.core.modulos.pago.dto.response;

import java.math.BigDecimal;

/** Lo que el navegador necesita para confirmar el pago con Stripe: el secreto del intento, el monto y la moneda. */
public record IniciarPagoResponse(String clientSecret, BigDecimal monto, String moneda) {
}
