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
        PlanSuscripcionEntidad plan = planPublico(codigoPlan, true);
        exigirSinSuscripcion(tiendaId);

        Instant ahora = reloj.instant();
        Instant fin = ahora.plus(plan.getDiasPrueba(), ChronoUnit.DAYS);

        SuscripcionTiendaEntidad suscripcion = nuevaSuscripcion(tiendaId, plan, EstadoSuscripcion.PRUEBA, ahora);
        suscripcion.setFechaFinPrueba(fin);
        suscripcion.setFinPeriodoActual(fin);
        return aVigente(suscripciones.save(suscripcion), plan);
    }

    /**
     * Crea la suscripción de una tienda cuyo propietario ya usó su prueba (SUS-016): nace {@code VENCIDA}
     * con período vacío y no opera hasta que se pague. No exige {@code DiasPrueba >= 1} del plan.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SuscripcionVigente crearPendienteDePago(UUID tiendaId, String codigoPlan) {
        PlanSuscripcionEntidad plan = planPublico(codigoPlan, false);
        exigirSinSuscripcion(tiendaId);

        Instant ahora = reloj.instant();
        SuscripcionTiendaEntidad suscripcion = nuevaSuscripcion(tiendaId, plan, EstadoSuscripcion.VENCIDA, ahora);
        suscripcion.setFinPeriodoActual(ahora);
        suscripcion.setFechaVencimiento(ahora);
        suscripcion.setMotivoEstado("Prueba gratuita ya utilizada; requiere pago");
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

    private PlanSuscripcionEntidad planPublico(String codigoPlan, boolean exigirDiasPrueba) {
        String codigo = codigoPlan == null || codigoPlan.isBlank()
                ? CODIGO_PLAN_POR_DEFECTO
                : codigoPlan.strip().toUpperCase(Locale.ROOT);

        return planes.findByCodigoAndEliminadoFalse(codigo)
                .filter(p -> Boolean.TRUE.equals(p.getEsPublico()) && (!exigirDiasPrueba || p.getDiasPrueba() >= 1))
                .orElseThrow(() -> new ReglaNegocioException("SUS-015", "El plan elegido no está disponible"));
    }

    private void exigirSinSuscripcion(UUID tiendaId) {
        if (suscripciones.buscarNoCanceladaPorTienda(tiendaId).isPresent()) {
            throw new ConflictoEstadoException("SUS-002", "La tienda ya tiene una suscripción vigente");
        }
    }

    private SuscripcionTiendaEntidad nuevaSuscripcion(UUID tiendaId, PlanSuscripcionEntidad plan,
                                                      EstadoSuscripcion estado, Instant ahora) {
        SuscripcionTiendaEntidad suscripcion = new SuscripcionTiendaEntidad();
        suscripcion.setTiendaId(tiendaId);
        suscripcion.setPlanId(plan.getId());
        suscripcion.setEstado(estado);
        suscripcion.setCicloFacturacion(CicloFacturacion.MENSUAL);
        suscripcion.setPrecioPactado(plan.getPrecioMensual());
        suscripcion.setMoneda(plan.getMoneda());
        suscripcion.setFechaInicio(ahora);
        suscripcion.setInicioPeriodoActual(ahora);
        suscripcion.setCancelarAlFinalizarPeriodo(false);
        return suscripcion;
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
