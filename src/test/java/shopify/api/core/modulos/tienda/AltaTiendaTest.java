package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import shopify.api.core.soporte.Rutas;

class AltaTiendaTest extends PruebaTiendaBase {

    @Test
    void altaValidaCreaTodasLasFilasRelacionadas() throws Exception {
        Sesion propietario = nuevaSesion();
        String subdominio = subdominioUnico();

        JsonNode respuesta = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominio), propietario.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.subdominio").value(subdominio))
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("PRUEBA"))
                .andExpect(jsonPath("$.datos.suscripcion.codigo_plan").value("BASICO"))
                .andExpect(jsonPath("$.datos.suscripcion.fin_periodo_actual").exists())
                .andExpect(jsonPath("$.datos.operativa").value(true)));
        UUID tiendaId = UUID.fromString(respuesta.get("datos").get("id").asText());

        Map<String, Object> tienda = jdbc.queryForMap("SELECT \"PropietarioId\", \"TasaImpuesto\", \"ZonaHoraria\", "
                + "\"SiguienteNumeroOrden\", \"Moneda\", \"EstaActiva\", \"PreciosIncluyenImpuesto\" "
                + "FROM \"Tiendas\" WHERE \"Id\" = ?", tiendaId);
        assertThat(tienda.get("PropietarioId")).isEqualTo(propietario.id());
        assertThat((BigDecimal) tienda.get("TasaImpuesto")).isEqualByComparingTo("0.18");
        assertThat(tienda.get("ZonaHoraria")).isEqualTo("America/Lima");
        assertThat(tienda.get("SiguienteNumeroOrden")).isEqualTo(1001);
        assertThat(tienda.get("Moneda")).isEqualTo("USD");
        assertThat(tienda.get("EstaActiva")).isEqualTo(true);
        assertThat(tienda.get("PreciosIncluyenImpuesto")).isEqualTo(true);

        List<String> roles = jdbc.queryForList("SELECT r.\"Codigo\" FROM \"PersonalTienda\" p "
                + "JOIN \"Roles\" r ON r.\"Id\" = p.\"RolId\" WHERE p.\"TiendaId\" = ? AND p.\"UsuarioId\" = ?",
                String.class, tiendaId, propietario.id());
        assertThat(roles).containsExactly("PROPIETARIO");

        BigDecimal ventas = jdbc.queryForObject(
                "SELECT \"TotalVentasHistorico\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", BigDecimal.class, tiendaId);
        assertThat(ventas).isEqualByComparingTo("0");

        Map<String, Object> suscripcion = jdbc.queryForMap("SELECT s.\"Estado\", p.\"Codigo\" FROM \"SuscripcionesTienda\" s "
                + "JOIN \"PlanesSuscripcion\" p ON p.\"Id\" = s.\"PlanId\" WHERE s.\"TiendaId\" = ?", tiendaId);
        assertThat(suscripcion.get("Estado")).isEqualTo("PRUEBA");
        assertThat(suscripcion.get("Codigo")).isEqualTo("BASICO");

        Map<String, Object> auditoria = jdbc.queryForMap("SELECT \"UsuarioId\", \"EntidadId\", \"Detalle\"::text AS detalle "
                + "FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = 'TIENDA_CREADA'", tiendaId);
        assertThat(auditoria.get("UsuarioId")).isEqualTo(propietario.id());
        assertThat(auditoria.get("EntidadId")).isEqualTo(tiendaId);
        JsonNode detalle = json.readTree((String) auditoria.get("detalle"));
        assertThat(detalle.get("subdominio").asText()).isEqualTo(subdominio);
        assertThat(detalle.get("plan").asText()).isEqualTo("BASICO");
        assertThat(detalle.get("estado_suscripcion").asText()).isEqualTo("PRUEBA");
    }

    @Test
    void altaConElPlanEstandar() throws Exception {
        Sesion propietario = nuevaSesion();
        Map<String, Object> cuerpo = cuerpoTienda(subdominioUnico());
        cuerpo.put("codigo_plan", "ESTANDAR");

        postJson(Rutas.CUENTA_TIENDAS, cuerpo, propietario.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.suscripcion.codigo_plan").value("ESTANDAR"))
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("PRUEBA"));
    }

    @Test
    void conUnPlanInexistenteNoQuedaNingunaFila() throws Exception {
        Sesion propietario = nuevaSesion();
        String subdominio = subdominioUnico();
        Map<String, Object> cuerpo = cuerpoTienda(subdominio);
        cuerpo.put("codigo_plan", "NO-EXISTE");

        postJson(Rutas.CUENTA_TIENDAS, cuerpo, propietario.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("SUS-015"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Tiendas\" WHERE \"Subdominio\" = ?", Integer.class, subdominio))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Tiendas\" WHERE \"PropietarioId\" = ?", Integer.class,
                propietario.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"PersonalTienda\" WHERE \"UsuarioId\" = ?", Integer.class,
                propietario.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"UsuarioId\" = ?", Integer.class,
                propietario.id())).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ab|El subdominio debe tener entre 3 y 63 caracteres",
            "-tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "tienda-|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "mi--tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "mi tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "admin|Ese subdominio está reservado"})
    void subdominiosInvalidosResponden422ConSuMensaje(String subdominio, String mensaje) throws Exception {
        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominio), nuevaSesion().token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIE-001"))
                .andExpect(jsonPath("$.mensaje").value(mensaje));
    }

    @Test
    void subdominioYaUsadoResponde422() throws Exception {
        String subdominio = subdominioUnico();
        crearTiendaPorApi(nuevaSesion(), subdominio);

        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominio), nuevaSesion().token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIE-001"))
                .andExpect(jsonPath("$.mensaje").value("Ese subdominio ya está en uso"));
    }

    @Test
    void subdominioDeUnaTiendaEliminadaTambienEstaOcupado() throws Exception {
        String subdominio = subdominioUnico();
        UUID eliminada = crearTiendaPorApi(nuevaSesion(), subdominio);
        eliminarTienda(eliminada);

        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominio), nuevaSesion().token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIE-001"))
                .andExpect(jsonPath("$.mensaje").value("Ese subdominio ya está en uso"));
    }

    @Test
    void subdominioValidoYNormalizadoAMinusculas() throws Exception {
        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda("mi-tienda-2"), nuevaSesion().token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.subdominio").value("mi-tienda-2"));

        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda("MI-TIENDA"), nuevaSesion().token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.subdominio").value("mi-tienda"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Tiendas\" WHERE \"Subdominio\" = 'mi-tienda'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void segundaTiendaDelMismoUsuarioNaceVencidaYNoSeVeEnLaVitrina() throws Exception {
        Sesion propietario = nuevaSesion();
        crearTiendaPorApi(propietario, subdominioUnico());
        String segundo = subdominioUnico();

        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(segundo), propietario.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("VENCIDA"))
                .andExpect(jsonPath("$.datos.operativa").value(false));

        getConToken(Rutas.vitrina(segundo), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
    }

    @Test
    void camposObligatoriosYCorreoSeValidan() throws Exception {
        postJson(Rutas.CUENTA_TIENDAS, Map.of("correo_contacto", "no-es-correo"), nuevaSesion().token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.nombre").exists())
                .andExpect(jsonPath("$.validaciones.subdominio").exists())
                .andExpect(jsonPath("$.validaciones.correo_contacto").exists());
    }

    @Test
    void sinSesionResponde401() throws Exception {
        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominioUnico()), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));
    }

    @Test
    void misTiendasListaLasPropiasLasDePersonalYOmiteEliminadas() throws Exception {
        Sesion usuario = nuevaSesion();
        UUID propia = crearTiendaPorApi(usuario, subdominioUnico());
        UUID ajena = crearTiendaPorApi(nuevaSesion(), subdominioUnico());
        agregarPersonal(ajena, usuario.id(), "GESTOR_PEDIDOS");
        UUID eliminada = crearTiendaPorApi(usuario, subdominioUnico());
        eliminarTienda(eliminada);

        JsonNode tiendas = cuerpo(getConToken(Rutas.CUENTA_TIENDAS, usuario.token(), null)
                .andExpect(status().isOk())).get("datos");

        assertThat(tiendas).hasSize(2);
        for (JsonNode t : tiendas) {
            String id = t.get("tienda_id").asText();
            if (id.equals(propia.toString())) {
                assertThat(t.get("rol").asText()).isEqualTo("PROPIETARIO");
                assertThat(t.get("operativa").asBoolean()).isTrue();
            } else {
                assertThat(id).isEqualTo(ajena.toString());
                assertThat(t.get("rol").asText()).isEqualTo("GESTOR_PEDIDOS");
                assertThat(t.get("operativa").asBoolean()).isTrue();
            }
            assertThat(t.get("esta_activa").asBoolean()).isTrue();
        }
    }

    @Test
    void ningunaRutaTiendasResponde() throws Exception {
        String token = nuevaSesion().token();

        getConToken(Rutas.RAIZ_TIENDAS_RETIRADA + "/mi-tienda", token, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-404"));
        getConToken(Rutas.RAIZ_TIENDAS_RETIRADA, token, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-404"));
    }
}
