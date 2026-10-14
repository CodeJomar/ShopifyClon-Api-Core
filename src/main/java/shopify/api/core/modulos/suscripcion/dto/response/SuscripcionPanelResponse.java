package shopify.api.core.modulos.suscripcion.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;

/** Suscripción vigente de la tienda vista por el propietario, con uso frente a límites (SUS-008, SUS-011). */
public record SuscripcionPanelResponse(
        String estado,
        String codigoPlan,
        String nombrePlan,
        CicloFacturacion ciclo,
        BigDecimal precioPactado,
        String moneda,
        Instant inicioPeriodo,
        Instant finPeriodo,
        Instant fechaFinPrueba,
        Boolean cancelarAlFinalizarPeriodo,
        UsoSuscripcion uso,
        LimitesSuscripcion limites) {

    public record UsoSuscripcion(long productos, long personal) {
    }

    /** {@code productos} nulo = ilimitado. */
    public record LimitesSuscripcion(Integer productos, int personal) {
    }
}
