package shopify.api.core.modulos.respaldo.servicio;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Programa el respaldo diario a la(s) hora(s) de {@code app.respaldo.hora} (formato HH:mm; varias separadas por coma, por
 * ejemplo «03:00,15:00») en la zona horaria de la aplicación. Solo corre si {@code app.respaldo.habilitado=true}. Una
 * hora mal escrita hace fallar el arranque a propósito: un respaldo que nunca se programa en silencio es peor que no
 * arrancar. La hora se cambia con la variable de entorno y un reinicio, sin tocar código.
 */
@Slf4j
@Component
public class RespaldoProgramador {

    private final RespaldoServicio respaldos;
    private final TaskScheduler planificador;
    private final Clock reloj;
    private final boolean habilitado;
    private final List<LocalTime> horas;

    public RespaldoProgramador(RespaldoServicio respaldos, TaskScheduler planificador, Clock reloj,
                               @Value("${app.respaldo.habilitado:false}") boolean habilitado,
                               @Value("${app.respaldo.hora:03:00}") String hora) {
        this.respaldos = respaldos;
        this.planificador = planificador;
        this.reloj = reloj;
        this.habilitado = habilitado;
        this.horas = habilitado ? parsear(hora) : List.of();
    }

    public boolean habilitado() {
        return habilitado;
    }

    public List<LocalTime> horas() {
        return horas;
    }

    @EventListener(ApplicationReadyEvent.class)
    void programar() {
        if (!habilitado) {
            log.info("Respaldo programado apagado (BACKUP_HABILITADO=false)");
            return;
        }
        if (!respaldos.directorioEscribible()) {
            log.error("BACKUP_HABILITADO=true pero la API no puede escribir en {}: los respaldos van a FALLAR. Monta un volumen "
                    + "(en Railway, en la ruta de BACKUP_DIRECTORIO) y revisa los permisos", respaldos.directorio());
        }
        ZoneId zona = respaldos.zona();
        for (LocalTime hora : horas) {
            String cron = "0 " + hora.getMinute() + " " + hora.getHour() + " * * *";
            planificador.schedule(this::ejecutar, new CronTrigger(cron, zona));
        }
        log.info("Respaldo programado todos los días a las {} ({}), se guardan {} días en {}", horas, zona,
                respaldos.retencionDias(), respaldos.directorio());
    }

    /** Próxima ejecución programada, si el respaldo está habilitado. */
    public Optional<ZonedDateTime> proxima() {
        ZonedDateTime ahora = ZonedDateTime.now(reloj.withZone(respaldos.zona()));
        return horas.stream().map(h -> {
            ZonedDateTime candidata = ahora.with(h);
            return candidata.isAfter(ahora) ? candidata : candidata.plusDays(1);
        }).min(Comparator.naturalOrder());
    }

    private void ejecutar() {
        try {
            respaldos.crear();
        } catch (RuntimeException ex) {
            // El servicio ya registró el motivo; el programador sigue vivo para la próxima hora
        }
    }

    static List<LocalTime> parsear(String texto) {
        List<LocalTime> horas = new ArrayList<>();
        for (String parte : (texto == null ? "" : texto).split(",")) {
            String limpia = parte.strip();
            if (limpia.isEmpty()) {
                continue;
            }
            try {
                horas.add(LocalTime.parse(limpia.length() == 4 && limpia.charAt(1) == ':' ? "0" + limpia : limpia));
            } catch (DateTimeParseException ex) {
                throw new IllegalStateException("BACKUP_HORA inválida: «" + limpia + "». Usa el formato HH:mm, por ejemplo 03:00 "
                        + "(varias horas separadas por coma)", ex);
            }
        }
        if (horas.isEmpty()) {
            throw new IllegalStateException("BACKUP_HABILITADO=true pero BACKUP_HORA está vacía. Usa el formato HH:mm, por ejemplo 03:00");
        }
        return horas.stream().distinct().sorted().toList();
    }
}
