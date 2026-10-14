package shopify.api.core.modulos.tienda.programador;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.repositorio.TiendaRepositorio;

/** SUS-007: desactiva la tienda cuya suscripción lleva más de 30 días VENCIDA. No borra nada. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TiendaProgramador {

    static final Duration PLAZO_VENCIDA = Duration.ofDays(30);
    static final String MOTIVO = "Suscripción vencida por más de 30 días";

    private final TiendaRepositorio tiendas;
    private final AuditoriaServicio auditoria;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    /** Mismo cron y bandera que el programador de suscripciones. */
    @Scheduled(cron = "#{${app.programador.habilitado:true} ? '${app.programador.suscripciones.cron:0 0 * * * *}' : '-'}")
    public void ejecutar() {
        Instant limite = reloj.instant().minus(PLAZO_VENCIDA);
        List<UUID> candidatas = tiendas.tiendasVencidasDesde(limite);
        for (UUID tiendaId : candidatas) {
            try {
                transaccion.executeWithoutResult(estado -> desactivar(tiendaId, limite));
            } catch (RuntimeException ex) {
                log.warn("No se pudo desactivar la tienda {} por vencimiento: {}", tiendaId, ex.getMessage());
            }
        }
    }

    private void desactivar(UUID tiendaId, Instant limite) {
        TiendaEntidad tienda = tiendas.bloquearPorId(tiendaId).orElse(null);
        if (tienda == null || !Boolean.TRUE.equals(tienda.getEstaActiva())
                || !tiendas.estaVencidaDesde(tiendaId, limite)) {
            return;
        }
        tienda.setEstaActiva(false);
        tiendas.save(tienda);
        auditoria.registrar(tiendaId, AccionAuditoria.TIENDA_DESACTIVADA, "Tiendas", tiendaId, MOTIVO,
                Map.of("antes", Map.of("esta_activa", true), "despues", Map.of("esta_activa", false)));
    }
}
