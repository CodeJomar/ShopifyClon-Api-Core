package shopify.api.core.modulos.pago.pasarela;

/**
 * Evento verificado de la pasarela. {@code intentoId} y {@code montoCentavos} solo vienen cuando el evento trae un
 * intento de cobro; para otros tipos son nulo y 0.
 */
public record EventoPasarela(String tipo, String intentoId, long montoCentavos) {
}
