package shopify.api.core.modulos.suscripcion.servicio;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.repositorio.PlanSuscripcionRepositorio;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;

/** Suscripción de cada tienda. Servicio interno: lo usa el alta de tienda (T-005). */
@Service
@RequiredArgsConstructor
public class SuscripcionServicio {

    static final String CODIGO_PLAN_POR_DEFECTO = "BASICO";

    private final PlanSuscripcionRepositorio planes;
    private final SuscripcionTiendaRepositorio suscripciones;
    private final Clock reloj;

    /**
     * Crea la prueba gratuita de la tienda (SUS-001). Sin plan o con el plan en blanco, usa BASICO.
     * Debe ejecutarse dentro de la transacción del alta de la tienda.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SuscripcionVigente crearPrueba(UUID tiendaId, String codigoPlan) {
        String codigo = codigoPlan == null || codigoPlan.isBlank()
                ? CODIGO_PLAN_POR_DEFECTO
                : codigoPlan.strip().toUpperCase(Locale.ROOT);

        PlanSuscripcionEntidad plan = planes.findByCodigoAndEliminadoFalse(codigo)
                .filter(p -> Boolean.TRUE.equals(p.getEsPublico()) && p.getDiasPrueba() >= 1)
                .orElseThrow(() -> new ReglaNegocioException("SUS-015", "El plan elegido no está disponible"));

        if (suscripciones.buscarNoCanceladaPorTienda(tiendaId).isPresent()) {
            throw new ConflictoEstadoException("SUS-002", "La tienda ya tiene una suscripción vigente");
        }

        Instant ahora = reloj.instant();
        Instant fin = ahora.plus(plan.getDiasPrueba(), ChronoUnit.DAYS);

        SuscripcionTiendaEntidad suscripcion = new SuscripcionTiendaEntidad();
        suscripcion.setTiendaId(tiendaId);
        suscripcion.setPlanId(plan.getId());
        suscripcion.setEstado(EstadoSuscripcion.PRUEBA);
        suscripcion.setCicloFacturacion(CicloFacturacion.MENSUAL);
        suscripcion.setPrecioPactado(plan.getPrecioMensual());
        suscripcion.setMoneda(plan.getMoneda());
        suscripcion.setFechaInicio(ahora);
        suscripcion.setFechaFinPrueba(fin);
        suscripcion.setInicioPeriodoActual(ahora);
        suscripcion.setFinPeriodoActual(fin);
        suscripcion.setCancelarAlFinalizarPeriodo(false);

        return aVigente(suscripciones.save(suscripcion), plan);
    }

    @Transactional(readOnly = true)
    public boolean propietarioYaUsoPrueba(UUID propietarioId) {
        return suscripciones.propietarioYaUsoPrueba(propietarioId);
    }

    /** Suscripción no cancelada de la tienda con los límites de su plan (vacío si no tiene). */
    @Transactional(readOnly = true)
    public Optional<SuscripcionVigente> vigente(UUID tiendaId) {
        return suscripciones.buscarNoCanceladaPorTienda(tiendaId)
                .flatMap(suscripcion -> planes.findById(suscripcion.getPlanId())
                        .map(plan -> aVigente(suscripcion, plan)));
    }

    private SuscripcionVigente aVigente(SuscripcionTiendaEntidad suscripcion, PlanSuscripcionEntidad plan) {
        return new SuscripcionVigente(
                suscripcion.getId(),
                suscripcion.getTiendaId(),
                plan.getCodigo(),
                suscripcion.getEstado(),
                suscripcion.getFinPeriodoActual(),
                plan.getLimiteProductos(),
                plan.getLimitePersonal(),
                plan.getPorcentajeComision());
    }
}
