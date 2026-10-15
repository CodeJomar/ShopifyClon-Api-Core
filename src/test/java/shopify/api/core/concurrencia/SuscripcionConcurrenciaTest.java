package shopify.api.core.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.modulos.suscripcion.PruebaPanelSuscripcionBase;
import shopify.api.core.modulos.suscripcion.programador.SuscripcionProgramador;

/** Suscripciones con cobros simultáneos: el programador y dos pagos del mismo período. */
class SuscripcionConcurrenciaTest extends PruebaPanelSuscripcionBase {

    @Autowired
    private SuscripcionProgramador programador;

    private Sesion propietario;
    private Tienda tienda;

    @BeforeEach
    void preparar() throws Exception {
        propietario = nuevaSesion();
        tienda = tiendaPorApi(propietario);
    }

    @Test
    void dosEjecucionesSimultaneasDelProgramadorRenuevanLaSuscripcionUnaSolaVez() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        UUID suscripcion = suscripcion();
        vencerPeriodo(suscripcion);

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> { programador.ejecutar(); return "ok"; },
                () -> { programador.ejecutar(); return "ok"; }));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        // Cobro inicial + una sola renovación del período vencido
        assertThat(cobros(tienda.id(), "APROBADO")).isEqualTo(2);
        Integer encadenado = jdbc.queryForObject("SELECT count(*) FROM \"CobrosSuscripcion\" WHERE \"SuscripcionId\" = ? "
                + "AND \"Estado\" = 'APROBADO'", Integer.class, suscripcion);
        assertThat(encadenado).isEqualTo(2);
    }

    @Test
    void dosPagosSimultaneosDeLaMismaSuscripcionDejanUnSoloCobroAprobado() throws Exception {
        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> estado(accionPanel("pagar", cuerpoPago("MENSUAL", "tok_uno"), propietario, tienda)),
                () -> estado(accionPanel("pagar", cuerpoPago("MENSUAL", "tok_dos"), propietario, tienda))));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        assertThat(resultados).extracting(Simultaneo.Resultado::valor).containsExactlyInAnyOrder(200, 409);
        assertThat(cobros(tienda.id(), "APROBADO")).isEqualTo(1);
    }

    private static int estado(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getStatus();
    }

    private UUID suscripcion() {
        return jdbc.queryForObject("SELECT \"Id\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE AND \"Estado\" <> 'CANCELADA'", UUID.class, tienda.id());
    }

    /** Mueve el período a un pasado de 1 día para que el programador lo tome (como en SuscripcionProgramadorTest). */
    private void vencerPeriodo(UUID suscripcion) {
        Instant fin = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"FinPeriodoActual\" = ?, \"InicioPeriodoActual\" = ? WHERE \"Id\" = ?",
                Timestamp.from(fin), Timestamp.from(fin.minus(31, ChronoUnit.DAYS)), suscripcion);
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"ReferenciaPasarela\" = ? WHERE \"Id\" = ?", "tok_ok", suscripcion);
    }
}
