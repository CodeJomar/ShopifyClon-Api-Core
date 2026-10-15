package shopify.api.core.modulos.suscripcion.programador;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionRenovadaEvento;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionVencidaEvento;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;
import shopify.api.core.modulos.suscripcion.repositorio.CobroSuscripcionRepositorio;
import shopify.api.core.modulos.suscripcion.repositorio.SuscripcionTiendaRepositorio;
import shopify.api.core.modulos.suscripcion.servicio.CobroSuscripcionServicio;

/**
 * Renovaciones y vencimientos automáticos (SUS-005, SUS-006). Cada suscripción se procesa en su propia
 * transacción; el cobro de renovación reutiliza el patrón T1 → pasarela → T2 de {@link CobroSuscripcionServicio}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuscripcionProgramador {

    static final int LOTE = 50;

    private final SuscripcionTiendaRepositorio suscripciones;
    private final CobroSuscripcionServicio cobros;
    private final CobroSuscripcionRepositorio cobrosRepositorio;
    private final PublicadorEvento publicador;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    /** Cron configurable; con {@code app.programador.habilitado=false} la tarea no se programa. */
    @Scheduled(cron = "#{${app.programador.habilitado:true} ? '${app.programador.suscripciones.cron:0 0 * * * *}' : '-'}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        // Cobros huérfanos (T-007): un PENDIENTE de más de una hora nunca recibió respuesta; se rechaza con motivo
        transaccion.executeWithoutResult(estado -> cobrosRepositorio.rechazarPendientesAnteriores(
                ahora.minus(Duration.ofHours(1)), "{\"motivo\": \"COBRO_HUERFANO\"}"));
        UUID ultimo = new UUID(0L, 0L);
        while (true) {
            UUID cursor = ultimo;
            List<UUID> lote = transaccion.execute(estado -> suscripciones.candidatasVencidas(ahora, cursor, LOTE));
            if (lote == null || lote.isEmpty()) {
                return;
            }
            for (UUID id : lote) {
                procesar(id, ahora);
            }
            ultimo = lote.get(lote.size() - 1);
        }
    }

    private void procesar(UUID suscripcionId, Instant ahora) {
        try {
            Decision decision = transaccion.execute(estado -> decidir(suscripcionId, ahora));
            if (decision == null) {
                return;
            }
            switch (decision.accion()) {
                case VENCER -> publicador.publicar(new SuscripcionVencidaEvento(decision.tiendaId()));
                case CANCELAR -> log.info("Suscripción {} cancelada al finalizar su período", suscripcionId);
                case RENOVAR -> publicarRenovacion(suscripcionId, cobros.renovar(suscripcionId));
                case OMITIR -> { }
            }
        } catch (RuntimeException ex) {
            // SUS-014: un cobro duplicado u otro conflicto se registra y se sigue con la siguiente suscripción
            log.warn("No se pudo procesar la suscripción {}: {}", suscripcionId, ex.getMessage());
        }
    }

    private void publicarRenovacion(UUID suscripcionId, CobroSuscripcionServicio.ResultadoRenovacion resultado) {
        switch (resultado.estado()) {
            case RENOVADA -> publicador.publicar(new SuscripcionRenovadaEvento(resultado.tiendaId()));
            case VENCIDA -> publicador.publicar(new SuscripcionVencidaEvento(resultado.tiendaId()));
            case OMITIDA -> log.info("Renovación de la suscripción {} omitida", suscripcionId);
        }
    }

    /** Decide sin cobrar: prueba vencida → VENCIDA; activa cancelada o sin token → CANCELADA/VENCIDA; con token → renovar. */
    private Decision decidir(UUID suscripcionId, Instant ahora) {
        Optional<SuscripcionTiendaEntidad> bloqueada = suscripciones.bloquearPorIdSinEsperar(suscripcionId);
        if (bloqueada.isEmpty()) {
            return null;
        }
        SuscripcionTiendaEntidad suscripcion = bloqueada.get();
        if (suscripcion.getFinPeriodoActual().isAfter(ahora)) {
            return null;
        }

        if (suscripcion.getEstado() == EstadoSuscripcion.PRUEBA) {
            suscripcion.setEstado(EstadoSuscripcion.VENCIDA);
            suscripcion.setFechaVencimiento(ahora);
            suscripciones.save(suscripcion);
            return new Decision(Accion.VENCER, suscripcion.getTiendaId());
        }
        if (suscripcion.getEstado() != EstadoSuscripcion.ACTIVA) {
            return null;
        }
        if (Boolean.TRUE.equals(suscripcion.getCancelarAlFinalizarPeriodo())) {
            suscripcion.setEstado(EstadoSuscripcion.CANCELADA);
            suscripcion.setFechaCancelacion(ahora);
            suscripciones.save(suscripcion);
            return new Decision(Accion.CANCELAR, suscripcion.getTiendaId());
        }
        if (suscripcion.getReferenciaPasarela() == null || suscripcion.getReferenciaPasarela().isBlank()) {
            suscripcion.setEstado(EstadoSuscripcion.VENCIDA);
            suscripcion.setFechaVencimiento(ahora);
            suscripciones.save(suscripcion);
            return new Decision(Accion.VENCER, suscripcion.getTiendaId());
        }
        return new Decision(Accion.RENOVAR, suscripcion.getTiendaId());
    }

    private record Decision(Accion accion, UUID tiendaId) {
    }

    private enum Accion {
        VENCER,
        CANCELAR,
        RENOVAR,
        OMITIR
    }
}
