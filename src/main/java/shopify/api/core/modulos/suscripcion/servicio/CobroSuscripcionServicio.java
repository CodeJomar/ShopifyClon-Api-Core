package shopify.api.core.modulos.suscripcion.servicio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.suscripcion.entidad.CobroSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;
import shopify.api.core.modulos.suscripcion.modelo.EstadoCobro;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.modelo.OperacionCobro;
import shopify.api.core.modulos.suscripcion.pasarela.PasarelaSuscripcion;
import shopify.api.core.modulos.suscripcion.pasarela.ResultadoCobro;
import shopify.api.core.modulos.suscripcion.repositorio.CobroSuscripcionRepositorio;
import shopify.api.core.modulos.suscripcion.repositorio.PlanSuscripcionRepositorio;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;

/**
 * Cobro de un período en tres pasos (PAG-010): T1 bloquea la suscripción y deja el cobro {@code PENDIENTE};
 * la pasarela se llama fuera de transacción; T2 registra el resultado y, si fue aprobado, aplica el efecto.
 */
@Service
@RequiredArgsConstructor
public class CobroSuscripcionServicio {

    static final String PASARELA_SIMULADA = "SIMULADA";

    private final SuscripcionTiendaRepositorio suscripciones;
    private final CobroSuscripcionRepositorio cobros;
    private final PlanSuscripcionRepositorio planes;
    private final PasarelaSuscripcion pasarela;
    private final UsoPlanServicio uso;
    private final AuditoriaServicio auditoria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    /** Cobra un período. Si la pasarela rechaza, lanza SUS-004 después de registrar el cobro como {@code RECHAZADO}. */
    public void cobrar(UUID tiendaId, PlanSuscripcionEntidad plan, CicloFacturacion ciclo, String tokenPago,
                       OperacionCobro operacion) {
        Pendiente pendiente = transaccion.execute(estado -> registrarPendiente(tiendaId, plan, ciclo, operacion));
        ResultadoCobro resultado = cobrarEnPasarela(pendiente, tokenPago);
        ResultadoAplicacion aplicacion = transaccion.execute(
                estado -> aplicar(pendiente, resultado, plan, ciclo, operacion, tiendaId));

        if (aplicacion == ResultadoAplicacion.RECHAZADO) {
            throw new ReglaNegocioException("SUS-004", "El pago fue rechazado");
        }
        if (aplicacion == ResultadoAplicacion.ESTADO_CAMBIADO) {
            throw new ConflictoEstadoException("SUS-004",
                    "La suscripción cambió mientras se cobraba. Intenta de nuevo");
        }
    }

    /** T1: validaciones con la suscripción bloqueada, y el cobro pendiente. */
    private Pendiente registrarPendiente(UUID tiendaId, PlanSuscripcionEntidad plan, CicloFacturacion ciclo,
                                         OperacionCobro operacion) {
        Instant ahora = reloj.instant();
        Optional<SuscripcionTiendaEntidad> actual = suscripciones.bloquearNoCanceladaPorTienda(tiendaId);
        SuscripcionTiendaEntidad suscripcion;
        String planAnterior = null;

        if (operacion == OperacionCobro.PAGO) {
            if (actual.isPresent()) {
                suscripcion = actual.get();
                EstadoSuscripcion estado = suscripcion.getEstado();
                if (estado == EstadoSuscripcion.ACTIVA) {
                    throw new ConflictoEstadoException("SUS-004", "Tu suscripción ya está activa");
                }
                if (estado != EstadoSuscripcion.PRUEBA && estado != EstadoSuscripcion.VENCIDA) {
                    throw new ConflictoEstadoException("SUS-004",
                            "No puedes pagar con la suscripción en estado " + estado.name());
                }
            } else {
                suscripcion = suscripciones.saveAndFlush(nuevaPendienteDePago(tiendaId, plan, ciclo, ahora));
            }
        } else {
            suscripcion = actual.orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción"));
            if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA) {
                throw new ConflictoEstadoException("SUS-008", "Solo puedes cambiar de plan con la suscripción activa");
            }
            uso.exigirDentroDeLimites(tiendaId, plan);
            planAnterior = planes.findById(suscripcion.getPlanId()).map(PlanSuscripcionEntidad::getCodigo).orElse("");
        }

        if (cobros.existsBySuscripcionIdAndEstado(suscripcion.getId(), EstadoCobro.PENDIENTE)) {
            throw new ConflictoEstadoException("SUS-004", "Ya hay un cobro en curso para tu suscripción");
        }

        BigDecimal monto = PreciosPlan.precioPara(plan, ciclo);
        CobroSuscripcionEntidad cobro = new CobroSuscripcionEntidad();
        cobro.setSuscripcionId(suscripcion.getId());
        cobro.setPlanId(plan.getId());
        cobro.setMonto(monto);
        cobro.setMoneda(plan.getMoneda());
        cobro.setInicioPeriodo(ahora);
        cobro.setFinPeriodo(PreciosPlan.finDePeriodo(ahora, ciclo));
        cobro.setEstado(EstadoCobro.PENDIENTE);
        cobro.setPasarelaPago(PASARELA_SIMULADA);
        cobro.setIntento(1);
        cobro = cobros.saveAndFlush(cobro);

        return new Pendiente(suscripcion.getId(), cobro.getId(), monto, plan.getMoneda(), planAnterior);
    }

    /** Fuera de transacción. Un error de la pasarela cuenta como rechazo: el cobro no se queda pendiente. */
    private ResultadoCobro cobrarEnPasarela(Pendiente pendiente, String tokenPago) {
        try {
            return pasarela.cobrar(pendiente.suscripcionId(), pendiente.monto(), pendiente.moneda(), tokenPago);
        } catch (RuntimeException ex) {
            return new ResultadoCobro(false, null);
        }
    }

    /** T2: registra el resultado y, si fue aprobado, aplica el período nuevo (SUS-003, SUS-004, SUS-008). */
    private ResultadoAplicacion aplicar(Pendiente pendiente, ResultadoCobro resultado, PlanSuscripcionEntidad plan,
                                        CicloFacturacion ciclo, OperacionCobro operacion, UUID tiendaId) {
        CobroSuscripcionEntidad cobro = cobros.findById(pendiente.cobroId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "No encontramos ese cobro"));
        SuscripcionTiendaEntidad suscripcion = suscripciones.bloquearPorId(pendiente.suscripcionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción"));

        cobro.setTransaccionId(resultado.transaccionId());
        cobro.setDetallesRespuesta(Map.<String, Object>of("aprobado", resultado.aprobado()));

        if (!resultado.aprobado()) {
            cobro.setEstado(EstadoCobro.RECHAZADO);
            return ResultadoAplicacion.RECHAZADO;
        }
        if (!suscripcion.getEstado().puedeTransicionarA(EstadoSuscripcion.ACTIVA)) {
            cobro.setEstado(EstadoCobro.RECHAZADO);
            cobro.setDetallesRespuesta(Map.<String, Object>of("motivo", "La suscripción cambió durante el cobro"));
            return ResultadoAplicacion.ESTADO_CAMBIADO;
        }

        Instant ahora = reloj.instant();
        cobro.setEstado(EstadoCobro.APROBADO);
        suscripcion.setEstado(EstadoSuscripcion.ACTIVA);
        suscripcion.setPlanId(plan.getId());
        suscripcion.setCicloFacturacion(ciclo);
        suscripcion.setPrecioPactado(pendiente.monto());
        suscripcion.setMoneda(plan.getMoneda());
        suscripcion.setInicioPeriodoActual(ahora);
        suscripcion.setFinPeriodoActual(PreciosPlan.finDePeriodo(ahora, ciclo));
        suscripcion.setFechaVencimiento(null);
        suscripcion.setMotivoEstado(null);
        suscripciones.save(suscripcion);

        if (operacion == OperacionCobro.CAMBIO_PLAN) {
            auditoria.registrar(tiendaId, AccionAuditoria.PLAN_CAMBIADO, "SuscripcionesTienda", suscripcion.getId(), null,
                    Map.of("antes", Map.of("plan", pendiente.planAnterior()),
                            "despues", Map.of("plan", plan.getCodigo(), "ciclo", ciclo.name())));
        }
        return ResultadoAplicacion.APLICADO;
    }

    /** Pago sin suscripción vigente: nace VENCIDA con período vacío y pasa a ACTIVA si el cobro se aprueba. */
    private SuscripcionTiendaEntidad nuevaPendienteDePago(UUID tiendaId, PlanSuscripcionEntidad plan,
                                                          CicloFacturacion ciclo, Instant ahora) {
        SuscripcionTiendaEntidad suscripcion = new SuscripcionTiendaEntidad();
        suscripcion.setTiendaId(tiendaId);
        suscripcion.setPlanId(plan.getId());
        suscripcion.setEstado(EstadoSuscripcion.VENCIDA);
        suscripcion.setCicloFacturacion(ciclo);
        suscripcion.setPrecioPactado(PreciosPlan.precioPara(plan, ciclo));
        suscripcion.setMoneda(plan.getMoneda());
        suscripcion.setFechaInicio(ahora);
        suscripcion.setInicioPeriodoActual(ahora);
        suscripcion.setFinPeriodoActual(ahora);
        suscripcion.setFechaVencimiento(ahora);
        suscripcion.setCancelarAlFinalizarPeriodo(false);
        suscripcion.setMotivoEstado("Pendiente de pago");
        return suscripcion;
    }

    private record Pendiente(UUID suscripcionId, UUID cobroId, BigDecimal monto, String moneda, String planAnterior) {
    }

    private enum ResultadoAplicacion {
        APLICADO,
        RECHAZADO,
        ESTADO_CAMBIADO
    }
}
