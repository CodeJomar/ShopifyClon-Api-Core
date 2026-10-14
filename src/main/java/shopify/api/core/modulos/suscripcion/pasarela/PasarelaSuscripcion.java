package shopify.api.core.modulos.suscripcion.pasarela;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cobro de un período de suscripción en una pasarela externa. Se llama siempre fuera de transacción
 * (PAG-010): un cobro real no debe ocurrir mientras la suscripción está bloqueada.
 */
public interface PasarelaSuscripcion {

    ResultadoCobro cobrar(UUID suscripcionId, BigDecimal monto, String moneda, String tokenPago);
}
