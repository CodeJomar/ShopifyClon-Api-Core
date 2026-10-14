package shopify.api.core.modulos.suscripcion.servicio;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.modulos.suscripcion.dto.request.CambiarPlanSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.request.PagarSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.response.SuscripcionPanelResponse;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.modelo.OperacionCobro;
import shopify.api.core.modulos.suscripcion.modelo.UsoTienda;
import shopify.api.core.modulos.suscripcion.repositorio.PlanSuscripcionRepositorio;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;

/** Suscripción de la tienda desde el panel del propietario (SUS-004, SUS-008, SUS-009). */
@Service
@RequiredArgsConstructor
public class SuscripcionPanelServicio {

    private final SuscripcionTiendaRepositorio suscripciones;
    private final PlanSuscripcionRepositorio planes;
    private final CobroSuscripcionServicio cobros;
    private final UsoPlanServicio uso;
    private final AuditoriaServicio auditoria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public SuscripcionPanelResponse ver(ContextoTienda contexto) {
        SuscripcionTiendaEntidad suscripcion = vigente(contexto.tiendaId());
        PlanSuscripcionEntidad plan = planPorId(suscripcion.getPlanId());
        UsoTienda usoActual = uso.uso(contexto.tiendaId());
        return new SuscripcionPanelResponse(
                suscripcion.getEstado().name(),
                plan.getCodigo(),
                plan.getNombre(),
                suscripcion.getCicloFacturacion(),
                suscripcion.getPrecioPactado(),
                suscripcion.getMoneda(),
                suscripcion.getInicioPeriodoActual(),
                suscripcion.getFinPeriodoActual(),
                suscripcion.getFechaFinPrueba(),
                suscripcion.getCancelarAlFinalizarPeriodo(),
                new SuscripcionPanelResponse.UsoSuscripcion(usoActual.productos(), usoActual.personal()),
                new SuscripcionPanelResponse.LimitesSuscripcion(plan.getLimiteProductos(), plan.getLimitePersonal()));
    }

    /** Sin suscripción vigente, o con el plan pedido: se cobra el período (SUS-004). */
    public SuscripcionPanelResponse pagar(ContextoTienda contexto, PagarSuscripcionRequest request) {
        PlanSuscripcionEntidad plan = planParaPagar(contexto.tiendaId(), request.codigoPlan());
        cobros.cobrar(contexto.tiendaId(), plan, request.ciclo(), request.tokenPago(), OperacionCobro.PAGO);
        return ver(contexto);
    }

    /** En prueba cambia plan y precio sin cobrar (conserva las fechas); en activa cobra el plan nuevo (SUS-008). */
    public SuscripcionPanelResponse cambiarPlan(ContextoTienda contexto, CambiarPlanSuscripcionRequest request) {
        UUID tiendaId = contexto.tiendaId();
        PlanSuscripcionEntidad plan = planPublico(request.codigoPlan());
        SuscripcionTiendaEntidad suscripcion = vigente(tiendaId);

        if (plan.getId().equals(suscripcion.getPlanId()) && suscripcion.getCicloFacturacion() == request.ciclo()) {
            throw new ConflictoEstadoException("SUS-008", "Ya tienes ese plan y ciclo de facturación");
        }
        if (suscripcion.getEstado() == EstadoSuscripcion.PRUEBA) {
            cambiarEnPrueba(tiendaId, plan, request);
        } else if (suscripcion.getEstado() == EstadoSuscripcion.ACTIVA) {
            cobros.cobrar(tiendaId, plan, request.ciclo(), request.tokenPago(), OperacionCobro.CAMBIO_PLAN);
        } else {
            throw new ConflictoEstadoException("SUS-008", "Solo puedes cambiar de plan en prueba o con la suscripción activa");
        }
        return ver(contexto);
    }

    /** En prueba cancela al momento; en activa programa la cancelación al final del período (SUS-009). */
    public void cancelar(ContextoTienda contexto, String motivo) {
        UUID tiendaId = contexto.tiendaId();
        String motivoLimpio = motivo.strip();
        transaccion.executeWithoutResult(estado -> {
            SuscripcionTiendaEntidad suscripcion = bloqueada(tiendaId);
            EstadoSuscripcion antes = suscripcion.getEstado();
            Instant ahora = reloj.instant();

            if (antes == EstadoSuscripcion.PRUEBA) {
                suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
                suscripcion.setFechaCancelacion(ahora);
                suscripcion.setMotivoEstado(motivoLimpio);
            } else if (antes == EstadoSuscripcion.ACTIVA) {
                if (Boolean.TRUE.equals(suscripcion.getCancelarAlFinalizarPeriodo())) {
                    throw new ConflictoEstadoException("SUS-009", "Tu suscripción ya tiene la cancelación programada");
                }
                suscripcion.setCancelarAlFinalizarPeriodo(true);
                suscripcion.setFechaCancelacion(ahora);
                suscripcion.setMotivoEstado(motivoLimpio);
            } else {
                throw new ConflictoEstadoException("SUS-009", "Solo puedes cancelar una prueba o una suscripción activa");
            }
            suscripciones.save(suscripcion);
            auditoria.registrar(tiendaId, AccionAuditoria.SUSCRIPCION_CANCELADA, "SuscripcionesTienda", suscripcion.getId(),
                    motivoLimpio, Map.of("antes", Map.of("estado", antes.name()),
                            "despues", Map.of("estado", suscripcion.getEstado().name(),
                                    "cancelar_al_finalizar_periodo", suscripcion.getCancelarAlFinalizarPeriodo())));
        });
    }

    /** Solo si la cancelación programada aún no llegó al fin de período (SUS-009). */
    public SuscripcionPanelResponse revertirCancelacion(ContextoTienda contexto) {
        UUID tiendaId = contexto.tiendaId();
        transaccion.executeWithoutResult(estado -> {
            SuscripcionTiendaEntidad suscripcion = bloqueada(tiendaId);
            boolean programada = suscripcion.getEstado() == EstadoSuscripcion.ACTIVA
                    && Boolean.TRUE.equals(suscripcion.getCancelarAlFinalizarPeriodo())
                    && suscripcion.getFinPeriodoActual().isAfter(reloj.instant());
            if (!programada) {
                throw new ConflictoEstadoException("SUS-009", "No hay una cancelación programada que revertir");
            }
            suscripcion.setCancelarAlFinalizarPeriodo(false);
            suscripcion.setFechaCancelacion(null);
            suscripcion.setMotivoEstado(null);
            suscripciones.save(suscripcion);
        });
        return ver(contexto);
    }

    private void cambiarEnPrueba(UUID tiendaId, PlanSuscripcionEntidad plan, CambiarPlanSuscripcionRequest request) {
        transaccion.executeWithoutResult(estado -> {
            SuscripcionTiendaEntidad suscripcion = bloqueada(tiendaId);
            if (suscripcion.getEstado() != EstadoSuscripcion.PRUEBA) {
                throw new ConflictoEstadoException("SUS-008", "La suscripción cambió; intenta de nuevo");
            }
            uso.exigirDentroDeLimites(tiendaId, plan);
            String antes = planPorId(suscripcion.getPlanId()).getCodigo();

            suscripcion.setPlanId(plan.getId());
            suscripcion.setCicloFacturacion(request.ciclo());
            suscripcion.setPrecioPactado(PreciosPlan.precioPara(plan, request.ciclo()));
            suscripcion.setMoneda(plan.getMoneda());
            suscripciones.save(suscripcion);
            auditoria.registrar(tiendaId, AccionAuditoria.PLAN_CAMBIADO, "SuscripcionesTienda", suscripcion.getId(), null,
                    Map.of("antes", Map.of("plan", antes),
                            "despues", Map.of("plan", plan.getCodigo(), "ciclo", request.ciclo().name())));
        });
    }

    /** Con {@code codigo_plan} ausente se usa el plan actual; si no hay suscripción, BASICO. */
    private PlanSuscripcionEntidad planParaPagar(UUID tiendaId, String codigoPlan) {
        if (codigoPlan == null || codigoPlan.isBlank()) {
            return suscripciones.buscarNoCanceladaPorTienda(tiendaId)
                    .map(suscripcion -> planPorId(suscripcion.getPlanId()))
                    .orElseGet(() -> planPublico(SuscripcionServicio.CODIGO_PLAN_POR_DEFECTO));
        }
        return planPublico(codigoPlan);
    }

    /** Solo los planes públicos se pueden elegir (SUS-015). */
    private PlanSuscripcionEntidad planPublico(String codigoPlan) {
        return planes.findByCodigoAndEliminadoFalse(codigoPlan.strip().toUpperCase(Locale.ROOT))
                .filter(plan -> Boolean.TRUE.equals(plan.getEsPublico()))
                .orElseThrow(() -> new ReglaNegocioException("SUS-015", "El plan elegido no está disponible"));
    }

    private SuscripcionTiendaEntidad vigente(UUID tiendaId) {
        return suscripciones.buscarNoCanceladaPorTienda(tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción vigente"));
    }

    private SuscripcionTiendaEntidad bloqueada(UUID tiendaId) {
        return suscripciones.bloquearNoCanceladaPorTienda(tiendaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción vigente"));
    }

    private PlanSuscripcionEntidad planPorId(UUID planId) {
        return planes.findById(planId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "No encontramos el plan de la suscripción"));
    }
}
