package shopify.api.core.modulos.pago.pasarela;

/** Respuesta de la pasarela a un reembolso: su id (nulo si no se creó) y si quedó aprobado. */
public record ResultadoReembolso(String id, boolean aprobado) {
}
