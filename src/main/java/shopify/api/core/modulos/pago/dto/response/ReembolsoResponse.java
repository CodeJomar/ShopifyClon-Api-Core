package shopify.api.core.modulos.pago.dto.response;

import shopify.api.core.modulos.orden.modelo.EstadoPago;

/** Reembolso creado y el estado de pago resultante de la orden. */
public record ReembolsoResponse(PagoResponse reembolso, EstadoPago estadoPago) {
}
