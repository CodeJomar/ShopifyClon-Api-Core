package shopify.api.core.modulos.suscripcion.servicio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;

/** Precio y fin de período de un plan según su ciclo (SUS-003). */
final class PreciosPlan {

    private PreciosPlan() {
    }

    static BigDecimal precioPara(PlanSuscripcionEntidad plan, CicloFacturacion ciclo) {
        return ciclo == CicloFacturacion.ANUAL ? plan.getPrecioAnual() : plan.getPrecioMensual();
    }

    static Instant finDePeriodo(Instant inicio, CicloFacturacion ciclo) {
        return ZonedDateTime.ofInstant(inicio, ZoneOffset.UTC).plus(ciclo.periodo()).toInstant();
    }
}
