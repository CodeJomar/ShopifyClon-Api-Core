package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.suscripcion.programador.SuscripcionProgramador;
import shopify.api.core.soporte.Rutas;

/**
 * T-022, pendiente 2 (SUS-003 de punta a punta): cambiar el precio del plan no altera el {@code PrecioPactado}
 * del período en curso; la siguiente renovación sí cobra el precio nuevo.
 */
class PrecioPlanSuscripcionTest extends PruebaPanelSuscripcionBase {

    @Autowired
    private SuscripcionProgramador programador;

    private Sesion administrador;
    private Sesion propietario;
    private Tienda tienda;
    private String codigoPlan;

    @BeforeEach
    void preparar() throws Exception {
        administrador = nuevaSesion(true);
        propietario = nuevaSesion();
        tienda = tiendaPorApi(propietario);
    }

    /** Antes que la limpieza de planes de la base: quita las suscripciones y cobros del plan propio (clave foránea). */
    @AfterEach
    void liberarPlanPropio() {
        if (codigoPlan == null) {
            return;
        }
        jdbc.update("DELETE FROM \"CobrosSuscripcion\" WHERE \"PlanId\" = (SELECT \"Id\" FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = ?)",
                codigoPlan);
        jdbc.update("DELETE FROM \"SuscripcionesTienda\" WHERE \"PlanId\" = (SELECT \"Id\" FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = ?)",
                codigoPlan);
    }

    @Test
    void cambiarElPrecioNoAlteraElPeriodoEnCursoYLaRenovacionCobraElPrecioNuevo() throws Exception {
        codigoPlan = codigoPlanDeLetras();
        recordarPlan(codigoPlan);
        Map<String, Object> plan = planPublico(codigoPlan, 50);
        postJson(Rutas.PLATAFORMA_PLANES, plan, administrador.token(), null).andExpect(status().isCreated());

        accionPanel("cambiar-plan", cuerpoCambio(codigoPlan, "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isOk());
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        UUID suscripcion = suscripcion();
        assertThat(estado(suscripcion)).isEqualTo("ACTIVA");
        assertThat(precioPactado(suscripcion)).isEqualByComparingTo("50.00");
        Instant finAntes = finPeriodo(suscripcion);

        Map<String, Object> cambio = new LinkedHashMap<>(planPublico(codigoPlan, 80));
        cambio.remove("codigo");
        putJson(Rutas.plataformaPlan(codigoPlan), cambio, administrador.token(), null).andExpect(status().isOk());

        // Período en curso: ni el precio pactado ni las fechas cambian al editar el plan
        assertThat(precioPactado(suscripcion)).isEqualByComparingTo("50.00");
        assertThat(finPeriodo(suscripcion)).isEqualTo(finAntes);

        // La renovación toma el precio vigente del plan (SUS-003 y SUS-005)
        vencerPeriodo(suscripcion);
        programador.ejecutar();

        List<BigDecimal> montos = jdbc.queryForList("SELECT \"Monto\" FROM \"CobrosSuscripcion\" WHERE \"SuscripcionId\" = ? "
                + "AND \"Estado\" = 'APROBADO' ORDER BY \"FechaCreacion\"", BigDecimal.class, suscripcion);
        assertThat(montos).hasSize(2);
        assertThat(montos.get(0)).isEqualByComparingTo("50.00");
        assertThat(montos.get(1)).isEqualByComparingTo("80.00");
        assertThat(precioPactado(suscripcion)).isEqualByComparingTo("80.00");
    }

    private Map<String, Object> planPublico(String codigo, int precioMensual) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("codigo", codigo);
        plan.put("nombre", "Plan de precio de prueba");
        plan.put("precio_mensual", precioMensual);
        plan.put("precio_anual", 500);
        plan.put("moneda", "USD");
        plan.put("limite_personal", 2);
        plan.put("porcentaje_comision", 0.01);
        plan.put("dias_prueba", 14);
        plan.put("es_publico", true);
        return plan;
    }

    private UUID suscripcion() {
        return jdbc.queryForObject("SELECT \"Id\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE AND \"Estado\" <> 'CANCELADA'", UUID.class, tienda.id());
    }

    private String estado(UUID suscripcion) {
        return jdbc.queryForObject("SELECT \"Estado\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", String.class, suscripcion);
    }

    private BigDecimal precioPactado(UUID suscripcion) {
        return jdbc.queryForObject("SELECT \"PrecioPactado\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", BigDecimal.class,
                suscripcion);
    }

    private Instant finPeriodo(UUID suscripcion) {
        return jdbc.queryForObject("SELECT \"FinPeriodoActual\" FROM \"SuscripcionesTienda\" WHERE \"Id\" = ?", Timestamp.class,
                suscripcion).toInstant();
    }

    /** Mueve el período a un pasado de 1 día para que el programador lo tome (como en SuscripcionConcurrenciaTest). */
    private void vencerPeriodo(UUID suscripcion) {
        Instant fin = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"FinPeriodoActual\" = ?, \"InicioPeriodoActual\" = ? WHERE \"Id\" = ?",
                Timestamp.from(fin), Timestamp.from(fin.minus(31, ChronoUnit.DAYS)), suscripcion);
    }
}
