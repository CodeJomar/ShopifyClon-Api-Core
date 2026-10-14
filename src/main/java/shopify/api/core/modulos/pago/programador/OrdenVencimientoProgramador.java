package shopify.api.core.modulos.pago.programador;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.pago.servicio.CancelacionOrdenServicio;

/**
 * ORD-008: anula las órdenes PENDIENTE cuyo plazo de pago venció. Procesa por lotes con cursor por {@code Id} y cada
 * orden en su propia transacción; una orden ya tomada por otra ejecución se omite (SKIP LOCKED).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrdenVencimientoProgramador {

    static final int LOTE = 50;

    private final JdbcTemplate jdbc;
    private final CancelacionOrdenServicio cancelacion;
    private final TransactionTemplate transaccion;
    private final Clock reloj;

    /** Cron configurable; con {@code app.programador.habilitado=false} la tarea no se programa. */
    @Scheduled(cron = "#{${app.programador.habilitado:true} ? '${app.programador.ordenes.cron:0 */5 * * * *}' : '-'}")
    public void ejecutar() {
        Instant ahora = reloj.instant();
        UUID ultimo = new UUID(0L, 0L);
        while (true) {
            List<UUID> lote = jdbc.queryForList(
                    "SELECT \"Id\" FROM \"Ordenes\" WHERE \"Eliminado\" = FALSE AND \"Estado\" = 'ABIERTA' "
                            + "AND \"EstadoPago\" = 'PENDIENTE' AND \"FechaExpiracionPago\" < ? AND \"Id\" > ? "
                            + "ORDER BY \"Id\" LIMIT ?",
                    UUID.class, Timestamp.from(ahora), ultimo, LOTE);
            if (lote.isEmpty()) {
                return;
            }
            for (UUID ordenId : lote) {
                procesar(ordenId, ahora);
            }
            ultimo = lote.get(lote.size() - 1);
        }
    }

    private void procesar(UUID ordenId, Instant ahora) {
        try {
            transaccion.executeWithoutResult(estado -> {
                // Volver a comprobar el vencimiento dentro del bloqueo: otra ejecución pudo pagar o anular la orden
                List<UUID> tiendas = jdbc.queryForList(
                        "SELECT \"TiendaId\" FROM \"Ordenes\" WHERE \"Id\" = ? AND \"Eliminado\" = FALSE "
                                + "AND \"Estado\" = 'ABIERTA' AND \"EstadoPago\" = 'PENDIENTE' "
                                + "AND \"FechaExpiracionPago\" < ? FOR UPDATE SKIP LOCKED",
                        UUID.class, ordenId, Timestamp.from(ahora));
                if (tiendas.isEmpty()) {
                    return;
                }
                cancelacion.anularSinPago(tiendas.get(0), ordenId, CancelacionOrdenServicio.MOTIVO_PAGO_VENCIDO, null);
            });
        } catch (RuntimeException ex) {
            // Una orden que falla no detiene el lote; queda para la siguiente ejecución
            log.warn("No se pudo anular la orden vencida {}: {}", ordenId, ex.getMessage());
        }
    }
}
