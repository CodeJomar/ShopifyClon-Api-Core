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
                estado -> aplicar(pendiente, resultado, plan, ciclo, operacion, tiendaId, tokenPago));

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
                                        CicloFacturacion ciclo, OperacionCobro operacion, UUID tiendaId,
                                        String tokenPago) {
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
        // SUS-018: el token (nunca datos de tarjeta) queda para la renovación automática
        suscripcion.setReferenciaPasarela(tokenPago);
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

    /**
     * Renovación automática (SUS-005, SUS-018): cobra el período siguiente con el token guardado y el precio
     * vigente del plan (SUS-003). El período nuevo empieza donde terminó el anterior. Si otra instancia tiene la
     * fila bloqueada, o ya hay un cobro en curso, se omite sin cobrar.
     */
    public ResultadoRenovacion renovar(UUID suscripcionId) {
        Instant ahora = reloj.instant();
        Optional<PendienteRenovacion> pendiente = transaccion.execute(estado -> registrarRenovacion(suscripcionId, ahora));
        if (pendiente.isEmpty()) {
            return new ResultadoRenovacion(ResultadoRenovacion.Estado.OMITIDA, null);
        }

        PendienteRenovacion p = pendiente.get();
        ResultadoCobro resultado = cobrarRenovacionEnPasarela(p);
        return transaccion.execute(estado -> aplicarRenovacion(p, resultado, ahora));
    }

    /** Fuera de transacción; un error de la pasarela cuenta como rechazo, igual que en {@link #cobrarEnPasarela}. */
    private ResultadoCobro cobrarRenovacionEnPasarela(PendienteRenovacion p) {
        try {
            return pasarela.cobrar(p.suscripcionId(), p.monto(), p.moneda(), p.token());
        } catch (RuntimeException ex) {
            return new ResultadoCobro(false, null);
        }
    }

    /** T1 de la renovación: bloquea sin esperar y deja el cobro {@code PENDIENTE} del período siguiente. */
    private Optional<PendienteRenovacion> registrarRenovacion(UUID suscripcionId, Instant ahora) {
        Optional<SuscripcionTiendaEntidad> bloqueada = suscripciones.bloquearPorIdSinEsperar(suscripcionId);
        if (bloqueada.isEmpty()) {
            return Optional.empty();
        }
        SuscripcionTiendaEntidad suscripcion = bloqueada.get();
        boolean renovable = suscripcion.getEstado() == EstadoSuscripcion.ACTIVA
                && !suscripcion.getFinPeriodoActual().isAfter(ahora)
                && !Boolean.TRUE.equals(suscripcion.getCancelarAlFinalizarPeriodo())
                && suscripcion.getReferenciaPasarela() != null && !suscripcion.getReferenciaPasarela().isBlank();
        if (!renovable || cobros.existsBySuscripcionIdAndEstado(suscripcion.getId(), EstadoCobro.PENDIENTE)) {
            return Optional.empty();
        }

        PlanSuscripcionEntidad plan = planes.findById(suscripcion.getPlanId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "No encontramos el plan de la suscripción"));
        BigDecimal monto = PreciosPlan.precioPara(plan, suscripcion.getCicloFacturacion());
        Instant inicio = suscripcion.getFinPeriodoActual();
        Instant fin = PreciosPlan.finDePeriodo(inicio, suscripcion.getCicloFacturacion());

        CobroSuscripcionEntidad cobro = new CobroSuscripcionEntidad();
        cobro.setSuscripcionId(suscripcion.getId());
        cobro.setPlanId(plan.getId());
        cobro.setMonto(monto);
        cobro.setMoneda(plan.getMoneda());
        cobro.setInicioPeriodo(inicio);
        cobro.setFinPeriodo(fin);
        cobro.setEstado(EstadoCobro.PENDIENTE);
        cobro.setPasarelaPago(PASARELA_SIMULADA);
        cobro.setIntento(1);
        cobro = cobros.saveAndFlush(cobro);

        return Optional.of(new PendienteRenovacion(suscripcion.getId(), suscripcion.getTiendaId(), cobro.getId(),
                monto, plan.getMoneda(), suscripcion.getReferenciaPasarela(), inicio, fin, suscripcion.getCicloFacturacion()));
    }

    /** T2 de la renovación: aprobado abre el período; rechazado lleva la suscripción a VENCIDA (SUS-005). */
    private ResultadoRenovacion aplicarRenovacion(PendienteRenovacion p, ResultadoCobro resultado, Instant ahora) {
        CobroSuscripcionEntidad cobro = cobros.findById(p.cobroId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "No encontramos ese cobro"));
        SuscripcionTiendaEntidad suscripcion = suscripciones.bloquearPorId(p.suscripcionId())
                .orElseThrow(() -> new RecursoNoEncontradoException("SUS-404", "La tienda no tiene una suscripción"));

        cobro.setTransaccionId(resultado.transaccionId());
        cobro.setDetallesRespuesta(Map.<String, Object>of("aprobado", resultado.aprobado(), "renovacion", true));

        if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA) {
            cobro.setEstado(EstadoCobro.RECHAZADO);
            return new ResultadoRenovacion(ResultadoRenovacion.Estado.OMITIDA, p.tiendaId());
        }
        if (!resultado.aprobado()) {
            cobro.setEstado(EstadoCobro.RECHAZADO);
            suscripcion.setEstado(EstadoSuscripcion.VENCIDA);
            suscripcion.setFechaVencimiento(ahora);
            suscripcion.setMotivoEstado("Cobro de renovación rechazado");
            suscripciones.save(suscripcion);
            return new ResultadoRenovacion(ResultadoRenovacion.Estado.VENCIDA, p.tiendaId());
        }

        cobro.setEstado(EstadoCobro.APROBADO);
        suscripcion.setInicioPeriodoActual(p.inicio());
        suscripcion.setFinPeriodoActual(p.fin());
        suscripcion.setCicloFacturacion(p.ciclo());
        suscripcion.setPrecioPactado(p.monto());
        suscripcion.setMoneda(p.moneda());
        suscripciones.save(suscripcion);
        return new ResultadoRenovacion(ResultadoRenovacion.Estado.RENOVADA, p.tiendaId());
    }

    /** Resultado de una renovación: el id de la tienda permite publicar el evento tras el commit. */
    public record ResultadoRenovacion(Estado estado, UUID tiendaId) {

        public enum Estado {
            RENOVADA,
            VENCIDA,
            OMITIDA
        }
    }

    private record PendienteRenovacion(UUID suscripcionId, UUID tiendaId, UUID cobroId, BigDecimal monto, String moneda,
                                       String token, Instant inicio, Instant fin, CicloFacturacion ciclo) {
    }

    private record Pendiente(UUID suscripcionId, UUID cobroId, BigDecimal monto, String moneda, String planAnterior) {
    }

    private enum ResultadoAplicacion {
        APLICADO,
        RECHAZADO,
        ESTADO_CAMBIADO
    }
}
