package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import shopify.api.core.modulos.suscripcion.programador.SuscripcionProgramador;
import shopify.api.core.modulos.tienda.programador.TiendaProgramador;

class SuscripcionProgramadorTest extends PruebaPanelSuscripcionBase {

    @Autowired
    private SuscripcionProgramador programador;

    @Autowired
    private ScheduledTaskHolder tareasProgramadas;

    private Sesion propietario;
    private Tienda tienda;

    @BeforeEach
    void preparar() throws Exception {
        propietario = nuevaSesion();
        tienda = tiendaPorApi(propietario);
    }

    private UUID suscripcion() {
        return jdbc.queryForObject("SELECT \"Id\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE AND \"Estado\" <> 'CANCELADA'", UUID.class, tienda.id());
    }

    private String estado(UUID suscripcionId) {
        return jdbc.queryForObject("SELECT \"Estado\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", String.class, suscripcionId);
    }

    /** Mueve el período a un pasado de 1 día (y su inicio a 31 días atrás) para que el programador lo tome. */
    private Instant vencerPeriodo(UUID suscripcionId) {
        Instant fin = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"FinPeriodoActual\" = ?, \"InicioPeriodoActual\" = ? WHERE \"Id\" = ?",
                Timestamp.from(fin), Timestamp.from(fin.minus(31, ChronoUnit.DAYS)), suscripcionId);
        return fin;
    }

    private void fijarReferencia(UUID suscripcionId, String token) {
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"ReferenciaPasarela\" = ? WHERE \"Id\" = ?", token, suscripcionId);
    }

    private int cobrosTotales(String estadoCobro) {
        return cobros(tienda.id(), estadoCobro);
    }

    @Test
    void pruebaVencidaPasaAVencidaYEjecutarDosVecesNoCambiaNada() {
        UUID suscripcionId = suscripcion();
        vencerPeriodo(suscripcionId);

        programador.ejecutar();
        Timestamp fechaVencimiento = jdbc.queryForObject(
                "SELECT \"FechaVencimiento\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", Timestamp.class, suscripcionId);
        assertThat(estado(suscripcionId)).isEqualTo("VENCIDA");
        assertThat(fechaVencimiento).isNotNull();

        programador.ejecutar();
        assertThat(estado(suscripcionId)).isEqualTo("VENCIDA");
        assertThat(jdbc.queryForObject("SELECT \"FechaVencimiento\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?",
                Timestamp.class, suscripcionId)).isEqualTo(fechaVencimiento);
        assertThat(cobrosTotales("APROBADO")).isZero();
    }

    @Test
    void pagarGuardaElTokenParaLaRenovacion() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT \"ReferenciaPasarela\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?",
                String.class, suscripcion())).isEqualTo("tok_ok");
    }

    @Test
    void activaVencidaConTokenSeRenuevaUnaSolaVezYEncadenaElPeriodo() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());
        UUID suscripcionId = suscripcion();
        Instant finAnterior = vencerPeriodo(suscripcionId);

        programador.ejecutar();

        assertThat(estado(suscripcionId)).isEqualTo("ACTIVA");
        assertThat(cobrosTotales("APROBADO")).isEqualTo(2);
        Integer encadenado = jdbc.queryForObject("SELECT count(*) FROM \"CobrosSuscripcion\" "
                + "WHERE \"SuscripcionId\" = ? AND \"Estado\" = 'APROBADO' AND \"InicioPeriodo\" = ?",
                Integer.class, suscripcionId, Timestamp.from(finAnterior));
        assertThat(encadenado).isEqualTo(1);
        Instant finNuevo = jdbc.queryForObject("SELECT \"FinPeriodoActual\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?",
                Timestamp.class, suscripcionId).toInstant();
        assertThat(finNuevo).isAfter(Instant.now());

        programador.ejecutar();
        assertThat(cobrosTotales("APROBADO")).isEqualTo(2);
    }

    @Test
    void activaVencidaConTokenRechazadoPasaAVencida() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());
        UUID suscripcionId = suscripcion();
        fijarReferencia(suscripcionId, "tok_rechazar");
        vencerPeriodo(suscripcionId);

        programador.ejecutar();

        assertThat(estado(suscripcionId)).isEqualTo("VENCIDA");
        assertThat(cobrosTotales("RECHAZADO")).isEqualTo(1);
        assertThat(cobrosTotales("APROBADO")).isEqualTo(1);
    }

    @Test
    void activaSinTokenPasaAVencidaSinCobrar() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());
        UUID suscripcionId = suscripcion();
        fijarReferencia(suscripcionId, null);
        vencerPeriodo(suscripcionId);

        programador.ejecutar();

        assertThat(estado(suscripcionId)).isEqualTo("VENCIDA");
        assertThat(cobrosTotales("APROBADO")).isEqualTo(1);
        assertThat(cobrosTotales("RECHAZADO")).isZero();
    }

    @Test
    void activaConCancelacionProgramadaPasaACancelada() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());
        accionPanel("cancelar", Map.of("motivo", "Ya no la necesito"), propietario, tienda).andExpect(status().isOk());
        UUID suscripcionId = jdbc.queryForObject("SELECT \"Id\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE ORDER BY \"FechaCreacion\" DESC LIMIT 1", UUID.class, tienda.id());
        vencerPeriodo(suscripcionId);

        programador.ejecutar();

        assertThat(estado(suscripcionId)).isEqualTo("CANCELADA");
        assertThat(cobrosTotales("APROBADO")).isEqualTo(1);
    }

    @Test
    void conLaBanderaApagadaNingunProgramadorQuedaProgramado() {
        List<String> programados = new ArrayList<>();
        for (ScheduledTask tarea : tareasProgramadas.getScheduledTasks()) {
            if (tarea.getTask().getRunnable() instanceof ScheduledMethodRunnable metodo) {
                programados.add(metodo.getMethod().getDeclaringClass().getSimpleName());
            }
        }
        assertThat(programados).doesNotContain(SuscripcionProgramador.class.getSimpleName(),
                TiendaProgramador.class.getSimpleName());
    }
}
