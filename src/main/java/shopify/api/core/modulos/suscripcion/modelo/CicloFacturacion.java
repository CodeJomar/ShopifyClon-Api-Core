package shopify.api.core.modulos.suscripcion.modelo;

import java.time.Period;

/** Ciclo de cobro de una suscripción (SUS-003, SUS-004). */
public enum CicloFacturacion {

    MENSUAL(Period.ofMonths(1)),
    ANUAL(Period.ofYears(1));

    private final Period periodo;

    CicloFacturacion(Period periodo) {
        this.periodo = periodo;
    }

    public Period periodo() {
        return periodo;
    }
}
