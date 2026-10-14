package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class SuscripcionPanelTest extends PruebaPanelSuscripcionBase {

    private Sesion propietario;
    private Tienda tienda;

    @BeforeEach
    void preparar() throws Exception {
        propietario = nuevaSesion();
        tienda = tiendaPorApi(propietario);
    }

    private long dias(JsonNode datos) {
        return ChronoUnit.DAYS.between(Instant.parse(datos.get("inicio_periodo").asText()),
                Instant.parse(datos.get("fin_periodo").asText()));
    }

    @Test
    void pagarDesdePruebaActivaElPeriodoMensual() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVA"));

        JsonNode datos = panel(propietario, tienda);
        assertThat(datos.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(dias(datos)).isBetween(28L, 31L);
        assertThat(cobros(tienda.id(), "APROBADO")).isEqualTo(1);
    }

    @Test
    void pagarAnualUsaElPrecioAnualYUnAnio() throws Exception {
        accionPanel("pagar", cuerpoPago("ANUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        JsonNode datos = panel(propietario, tienda);
        assertThat(dias(datos)).isBetween(365L, 366L);
        assertThat(new BigDecimal(datos.get("precio_pactado").asText())).isEqualByComparingTo("290.00");
    }

    @Test
    void tokenRechazadoDejaElEstadoYRegistraElCobro() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_rechazar"), propietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SUS-004"));

        assertThat(panel(propietario, tienda).get("estado").asText()).isEqualTo("PRUEBA");
        assertThat(cobros(tienda.id(), "RECHAZADO")).isEqualTo(1);
    }

    @Test
    void pagarCuandoYaEstaActivaEs409() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-004"));
    }

    @Test
    void laSegundaTiendaNaceVencidaYSeActivaAlPagar() throws Exception {
        Tienda segunda = tiendaPorApi(propietario);
        assertThat(panel(propietario, segunda).get("estado").asText()).isEqualTo("VENCIDA");
        getConToken(Rutas.vitrina(segunda.subdominio()), null, null).andExpect(status().isNotFound());

        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, segunda).andExpect(status().isOk());

        getConToken(Rutas.vitrina(segunda.subdominio()), null, null).andExpect(status().isOk());
    }

    @Test
    void cambiarPlanEnPruebaNoCobra() throws Exception {
        accionPanel("cambiar-plan", cuerpoCambio("ESTANDAR", "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isOk());

        JsonNode datos = panel(propietario, tienda);
        assertThat(datos.get("codigo_plan").asText()).isEqualTo("ESTANDAR");
        assertThat(datos.get("estado").asText()).isEqualTo("PRUEBA");
        assertThat(cobros(tienda.id(), "APROBADO")).isZero();
        assertThat(cobros(tienda.id(), "PENDIENTE")).isZero();
    }

    @Test
    void mismoPlanYCicloEs409() throws Exception {
        accionPanel("cambiar-plan", cuerpoCambio("BASICO", "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-008"));
    }

    @Test
    void planNoPublicoNoSePuedeElegirEs422() throws Exception {
        String privado = codigoPlanDeLetras();
        insertarPlan(privado, false, false, 14);
        recordarPlan(privado);

        accionPanel("cambiar-plan", cuerpoCambio(privado, "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SUS-015"));
    }

    @Test
    void cambiarPlanEnActivaCobraYAudita() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        accionPanel("cambiar-plan", cuerpoCambio("ESTANDAR", "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isOk());

        assertThat(panel(propietario, tienda).get("codigo_plan").asText()).isEqualTo("ESTANDAR");
        assertThat(cobros(tienda.id(), "APROBADO")).isEqualTo(2);
        assertThat(auditoria(tienda.id(), "PLAN_CAMBIADO")).isEqualTo(1);
    }

    @Test
    void bajarConMasPersonalQueElLimiteEs422ConCuantoSobra() throws Exception {
        accionPanel("cambiar-plan", cuerpoCambio("ESTANDAR", "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isOk());
        personalConRol(tienda.id(), "GESTOR_PEDIDOS");
        personalConRol(tienda.id(), "GESTOR_PEDIDOS");
        personalConRol(tienda.id(), "GESTOR_PEDIDOS");

        accionPanel("cambiar-plan", cuerpoCambio("BASICO", "MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SUS-008"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Quita 1")));
    }

    @Test
    void cancelarEnPruebaEsInmediataYLaTiendaDejaDeVender() throws Exception {
        accionPanel("cancelar", java.util.Map.of("motivo", "Ya no la necesito"), propietario, tienda)
                .andExpect(status().isOk());

        getConToken(Rutas.PANEL_SUSCRIPCION, propietario.token(), tienda.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SUS-404"));
        getConToken(Rutas.vitrina(tienda.subdominio()), null, null).andExpect(status().isNotFound());
    }

    @Test
    void cancelarEnActivaSoloProgramaYRevertirFunciona() throws Exception {
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());
        accionPanel("cancelar", java.util.Map.of("motivo", "Cambio de planes"), propietario, tienda)
                .andExpect(status().isOk());

        JsonNode datos = panel(propietario, tienda);
        assertThat(datos.get("estado").asText()).isEqualTo("ACTIVA");
        assertThat(datos.get("cancelar_al_finalizar_periodo").asBoolean()).isTrue();
        getConToken(Rutas.vitrina(tienda.subdominio()), null, null).andExpect(status().isOk());

        accionPanel("revertir-cancelacion", null, propietario, tienda).andExpect(status().isOk());
        assertThat(panel(propietario, tienda).get("cancelar_al_finalizar_periodo").asBoolean()).isFalse();

        accionPanel("revertir-cancelacion", null, propietario, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-009"));
    }

    @Test
    void soloElPropietarioGestionaLaSuscripcion() throws Exception {
        Sesion gestor = personalConRol(tienda.id(), "GESTOR_PEDIDOS");
        Sesion administrador = personalConRol(tienda.id(), "ADMINISTRADOR");

        getConToken(Rutas.PANEL_SUSCRIPCION, gestor.token(), tienda.id()).andExpect(status().isForbidden());
        getConToken(Rutas.PANEL_SUSCRIPCION, administrador.token(), tienda.id()).andExpect(status().isForbidden());
    }

    @Test
    void elUsoCuentaElPersonalSinElPropietario() throws Exception {
        personalConRol(tienda.id(), "GESTOR_PEDIDOS");

        JsonNode datos = panel(propietario, tienda);
        assertThat(datos.get("uso").get("personal").asLong()).isEqualTo(1L);
        assertThat(datos.get("limites").get("personal").asInt()).isEqualTo(2);
        assertThat(datos.get("limites").get("productos").asInt()).isEqualTo(100);
    }
}
