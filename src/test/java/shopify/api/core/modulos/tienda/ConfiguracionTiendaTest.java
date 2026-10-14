package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class ConfiguracionTiendaTest extends PruebaTiendaBase {

    private Sesion propietario;
    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() throws Exception {
        propietario = nuevaSesion();
        subdominio = subdominioUnico();
        tienda = crearTiendaPorApi(propietario, subdominio);
    }

    private Map<String, Object> cambios() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Nombre nuevo");
        cuerpo.put("correo_contacto", "NUEVO@contacto.test");
        cuerpo.put("moneda", "USD");
        cuerpo.put("tasa_impuesto", "0.10");
        cuerpo.put("precios_incluyen_impuesto", false);
        cuerpo.put("zona_horaria", "America/Bogota");
        return cuerpo;
    }

    private void insertarOrden() {
        jdbc.update("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", \"NombreCliente\", "
                + "\"Subtotal\", \"Total\") VALUES (?, '#1001', 'cliente@prueba.test', 'Cliente', 10.00, 10.00)", tienda);
    }

    @Test
    void cualquierMiembroVeLaConfiguracionSinElNumeroDeOrden() throws Exception {
        Sesion gestor = personalConRol(tienda, "GESTOR_CATALOGO");

        JsonNode datos = cuerpo(getConToken(Rutas.PANEL_TIENDA, gestor.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.subdominio").value(subdominio))
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("PRUEBA"))
                .andExpect(jsonPath("$.datos.operativa").value(true))).get("datos");

        assertThat(datos.has("siguiente_numero_orden")).isFalse();
        assertThat(datos.get("tasa_impuesto").decimalValue()).isEqualByComparingTo("0.18");
        assertThat(datos.get("zona_horaria").asText()).isEqualTo("America/Lima");
    }

    @Test
    void gestorDeCatalogoNoPuedeEditar() throws Exception {
        Sesion gestor = personalConRol(tienda, "GESTOR_CATALOGO");

        putJson(Rutas.PANEL_TIENDA, cambios(), gestor.token(), tienda)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
    }

    @Test
    void administradorEditaYQuedaPersistido() throws Exception {
        Sesion admin = personalConRol(tienda, "ADMINISTRADOR");

        putJson(Rutas.PANEL_TIENDA, cambios(), admin.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombre").value("Nombre nuevo"))
                .andExpect(jsonPath("$.datos.zona_horaria").value("America/Bogota"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT \"Nombre\", \"CorreoContacto\", \"TasaImpuesto\", "
                + "\"PreciosIncluyenImpuesto\", \"ZonaHoraria\", \"SiguienteNumeroOrden\" FROM \"Tiendas\" WHERE \"Id\" = ?", tienda);
        assertThat(fila.get("Nombre")).isEqualTo("Nombre nuevo");
        assertThat(fila.get("CorreoContacto")).isEqualTo("nuevo@contacto.test");
        assertThat((BigDecimal) fila.get("TasaImpuesto")).isEqualByComparingTo("0.10");
        assertThat(fila.get("PreciosIncluyenImpuesto")).isEqualTo(false);
        assertThat(fila.get("ZonaHoraria")).isEqualTo("America/Bogota");
        assertThat(fila.get("SiguienteNumeroOrden")).isEqualTo(1001);
    }

    @Test
    void tasaFueraDeRangoResponde422() throws Exception {
        Map<String, Object> cuerpo = cambios();
        cuerpo.put("tasa_impuesto", "0.51");

        putJson(Rutas.PANEL_TIENDA, cuerpo, propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.tasa_impuesto").exists());
    }

    @Test
    void zonaHorariaInvalidaResponde422() throws Exception {
        Map<String, Object> cuerpo = cambios();
        cuerpo.put("zona_horaria", "Marte/Olimpo");

        putJson(Rutas.PANEL_TIENDA, cuerpo, propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("TIE-006"));
    }

    @Test
    void cambiarLaMonedaConOrdenesResponde409() throws Exception {
        insertarOrden();
        Map<String, Object> cuerpo = cambios();
        cuerpo.put("moneda", "PEN");

        putJson(Rutas.PANEL_TIENDA, cuerpo, propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIE-005"));

        // mantener la moneda sí se permite aunque haya órdenes
        putJson(Rutas.PANEL_TIENDA, cambios(), propietario.token(), tienda).andExpect(status().isOk());
    }

    @Test
    void sinOrdenesLaMonedaSePuedeCambiar() throws Exception {
        Map<String, Object> cuerpo = cambios();
        cuerpo.put("moneda", "PEN");

        putJson(Rutas.PANEL_TIENDA, cuerpo, propietario.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.moneda").value("PEN"));
    }

    @Test
    void administradorNoPuedeDesactivar() throws Exception {
        Sesion admin = personalConRol(tienda, "ADMINISTRADOR");

        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of(), admin.token(), tienda)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
        postJson(Rutas.PANEL_TIENDA_ACTIVAR, Map.of(), admin.token(), tienda)
                .andExpect(status().isForbidden());
    }

    @Test
    void propietarioDesactivaYActivaConAuditoria() throws Exception {
        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Vacaciones de temporada"), propietario.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.esta_activa").value(false))
                .andExpect(jsonPath("$.datos.operativa").value(false));

        assertThat(contarAuditoria(tienda, "TIENDA_DESACTIVADA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"Motivo\" FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? "
                + "AND \"Accion\" = 'TIENDA_DESACTIVADA'", String.class, tienda)).isEqualTo("Vacaciones de temporada");

        getConToken(Rutas.vitrina(subdominio), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
        // el panel sigue respondiendo
        getConToken(Rutas.PANEL_TIENDA, propietario.token(), tienda).andExpect(status().isOk());

        postJson(Rutas.PANEL_TIENDA_ACTIVAR, Map.of(), propietario.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.esta_activa").value(true))
                .andExpect(jsonPath("$.datos.operativa").value(true));
        assertThat(contarAuditoria(tienda, "TIENDA_ACTIVADA")).isEqualTo(1);
        getConToken(Rutas.vitrina(subdominio), null, null).andExpect(status().isOk());
    }

    @Test
    void activarUnaTiendaYaActivaNoAudita() throws Exception {
        postJson(Rutas.PANEL_TIENDA_ACTIVAR, Map.of(), propietario.token(), tienda).andExpect(status().isOk());
        // sin cuerpo tampoco falla
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(Rutas.PANEL_TIENDA_ACTIVAR)
                        .header("Authorization", "Bearer " + propietario.token())
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isOk());

        assertThat(contarAuditoria(tienda, "TIENDA_ACTIVADA")).isZero();
    }

    @Test
    void laVitrinaMuestraSoloLosCincoCamposPublicos() throws Exception {
        JsonNode datos = cuerpo(getConToken(Rutas.vitrina(subdominio.toUpperCase()), null, null)
                .andExpect(status().isOk())).get("datos");

        List<String> campos = new ArrayList<>();
        datos.fieldNames().forEachRemaining(campos::add);
        assertThat(campos).containsExactlyInAnyOrder(
                "nombre", "subdominio", "moneda", "correo_contacto", "precios_incluyen_impuesto");
        assertThat(datos.get("subdominio").asText()).isEqualTo(subdominio);
    }

    @Test
    void vitrinaDeUnaTiendaInexistenteOEliminadaResponde404() throws Exception {
        getConToken(Rutas.vitrina("no-existe-" + sufijo()), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"))
                .andExpect(jsonPath("$.mensaje").value("Esta tienda no está disponible"));

        eliminarTienda(tienda);
        getConToken(Rutas.vitrina(subdominio), null, null).andExpect(status().isNotFound());
    }

    @Test
    void sinEncabezadoDeTiendaElPanelResponde400() throws Exception {
        getConToken(Rutas.PANEL_TIENDA, propietario.token(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }
}
