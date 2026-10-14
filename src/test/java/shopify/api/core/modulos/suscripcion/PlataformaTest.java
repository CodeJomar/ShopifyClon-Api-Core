package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class PlataformaTest extends PruebaPanelSuscripcionBase {

    private Sesion administrador;

    @BeforeEach
    void preparar() {
        administrador = nuevaSesion(true);
    }

    private Map<String, Object> planJson(String codigo, Object precioMensual, Object porcentajeComision) {
        Map<String, Object> cuerpoPlan = new LinkedHashMap<>();
        cuerpoPlan.put("codigo", codigo);
        cuerpoPlan.put("nombre", "Plan de prueba");
        cuerpoPlan.put("precio_mensual", precioMensual);
        cuerpoPlan.put("precio_anual", 100);
        cuerpoPlan.put("moneda", "USD");
        cuerpoPlan.put("limite_personal", 2);
        cuerpoPlan.put("porcentaje_comision", porcentajeComision);
        cuerpoPlan.put("dias_prueba", 14);
        cuerpoPlan.put("es_publico", false);
        return cuerpoPlan;
    }

    private UUID idDelPlan(String codigo) {
        return jdbc.queryForObject("SELECT \"Id\" FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = ?", UUID.class, codigo);
    }

    @Test
    void noAdministradorRecibe404SEG006() throws Exception {
        Sesion usuario = nuevaSesion();

        getConToken(Rutas.PLATAFORMA_PLANES, usuario.token(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-006"));
        postJson(Rutas.plataformaTienda(UUID.randomUUID(), "suspender"), Map.of("motivo", "Prueba"), usuario.token(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-006"));
    }

    @Test
    void crearValidaYRechazaCodigosDuplicadosYValoresFuera() throws Exception {
        String codigo = codigoPlanDeLetras();
        recordarPlan(codigo);
        postJson(Rutas.PLATAFORMA_PLANES, planJson(codigo, 50, 0.01), administrador.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.es_publico").value(false));

        postJson(Rutas.PLATAFORMA_PLANES, planJson(codigo, 50, 0.01), administrador.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        postJson(Rutas.PLATAFORMA_PLANES, planJson("plan-x", 50, 0.01), administrador.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        postJson(Rutas.PLATAFORMA_PLANES, planJson(codigoPlanDeLetras(), 50, 0.5), administrador.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        postJson(Rutas.PLATAFORMA_PLANES, planJson(codigoPlanDeLetras(), -1, 0.01), administrador.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
    }

    @Test
    void cambiarUnPrecioSeAuditaYNoCambiaElCodigo() throws Exception {
        String codigo = codigoPlanDeLetras();
        recordarPlan(codigo);
        postJson(Rutas.PLATAFORMA_PLANES, planJson(codigo, 50, 0.01), administrador.token(), null)
                .andExpect(status().isCreated());

        Map<String, Object> cambio = planJson(codigo, 60, 0.01);
        cambio.remove("codigo");
        putJson(Rutas.plataformaPlan(codigo), cambio, administrador.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.codigo").value(codigo))
                .andExpect(jsonPath("$.datos.precio_mensual").value(60));

        Integer auditoria = jdbc.queryForObject(
                "SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"EntidadId\" = ? AND \"Accion\" = 'PLAN_PRECIO_CAMBIADO'",
                Integer.class, idDelPlan(codigo));
        assertThat(auditoria).isEqualTo(1);
    }

    @Test
    void retirarUnPlanLoOcultaDelCatalogoPublicoSinBorrarlo() throws Exception {
        String codigo = codigoPlanDeLetras();
        recordarPlan(codigo);
        Map<String, Object> plan = planJson(codigo, 50, 0.01);
        plan.put("es_publico", true);
        postJson(Rutas.PLATAFORMA_PLANES, plan, administrador.token(), null).andExpect(status().isCreated());

        delete(Rutas.plataformaPlan(codigo), administrador.token(), null).andExpect(status().isOk());

        JsonNode planes = cuerpo(getConToken(Rutas.PLATAFORMA_PLANES, administrador.token(), null)
                .andExpect(status().isOk())).get("datos");
        boolean encontrado = false;
        for (JsonNode p : planes) {
            if (p.get("codigo").asText().equals(codigo)) {
                encontrado = true;
                assertThat(p.get("es_publico").asBoolean()).isFalse();
            }
        }
        assertThat(encontrado).isTrue();
        JsonNode publicos = cuerpo(getConToken(Rutas.PLANES, null, null).andExpect(status().isOk())).get("datos");
        for (JsonNode p : publicos) {
            assertThat(p.get("codigo").asText()).isNotEqualTo(codigo);
        }
    }

    @Test
    void listarSuscripcionesFiltraPorEstadoYRechazaUnEstadoInvalido() throws Exception {
        Sesion propietario = nuevaSesion();
        Tienda tienda = tiendaPorApi(propietario);

        JsonNode filas = cuerpo(getConToken(Rutas.PLATAFORMA_SUSCRIPCIONES + "?estado=PRUEBA", administrador.token(), null)
                .andExpect(status().isOk())).get("datos").get("datos");
        boolean encontrada = false;
        for (JsonNode fila : filas) {
            if (fila.get("subdominio").asText().equals(tienda.subdominio())) {
                encontrada = true;
                assertThat(fila.get("estado").asText()).isEqualTo("PRUEBA");
            }
        }
        assertThat(encontrada).isTrue();

        getConToken(Rutas.PLATAFORMA_SUSCRIPCIONES + "?estado=INVENTADO", administrador.token(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VAL-003"));
    }

    @Test
    void suspenderYReactivarRestauranElEstadoAnterior() throws Exception {
        Sesion propietario = nuevaSesion();
        Tienda tienda = tiendaPorApi(propietario);
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda).andExpect(status().isOk());

        postJson(Rutas.plataformaTienda(tienda.id(), "suspender"), Map.of("motivo", "Revisión de pagos"),
                administrador.token(), null).andExpect(status().isOk());

        assertThat(panel(propietario, tienda).get("estado").asText()).isEqualTo("SUSPENDIDA");
        getConToken(Rutas.vitrina(tienda.subdominio()), null, null).andExpect(status().isNotFound());
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-004"));
        postJson(Rutas.plataformaTienda(tienda.id(), "suspender"), Map.of("motivo", "Otra vez"),
                administrador.token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-010"));

        postJson(Rutas.plataformaTienda(tienda.id(), "reactivar"), Map.of("motivo", "Pago recibido"),
                administrador.token(), null).andExpect(status().isOk());

        assertThat(panel(propietario, tienda).get("estado").asText()).isEqualTo("ACTIVA");
        getConToken(Rutas.vitrina(tienda.subdominio()), null, null).andExpect(status().isOk());
        postJson(Rutas.plataformaTienda(tienda.id(), "reactivar"), Map.of("motivo", "Otra vez"),
                administrador.token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-010"));
        assertThat(auditoria(tienda.id(), "TIENDA_SUSPENDIDA")).isEqualTo(1);
        assertThat(auditoria(tienda.id(), "TIENDA_REACTIVADA")).isEqualTo(1);
    }
}
