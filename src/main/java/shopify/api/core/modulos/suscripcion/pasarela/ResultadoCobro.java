package shopify.api.core.modulos.suscripcion.pasarela;

/** Respuesta de la pasarela a un cobro: aprobado o no, con el id de la transacción. */
public record ResultadoCobro(boolean aprobado, String transaccionId) {
}
