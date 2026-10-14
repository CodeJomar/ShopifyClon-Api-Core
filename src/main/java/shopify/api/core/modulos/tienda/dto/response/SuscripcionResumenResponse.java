package shopify.api.core.modulos.tienda.dto.response;

import java.time.Instant;

/** Resumen de la suscripción de una tienda para el propietario y el panel. */
public record SuscripcionResumenResponse(String estado, String codigoPlan, Instant finPeriodoActual) {
}
