package shopify.api.core.modulos.cupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class CuponTest extends PruebaCuponBase {

    private Sesion propietario;
    private UUID tienda;

    @BeforeEach
    void preparar() {
        propietario = nuevaSesion();
        tienda = crearTienda(propietario.id());
    }

    private UUID crearCupon(String codigo, String tipo, Object valor) throws Exception {
        JsonNode datos = cuerpo(postJson(Rutas.PANEL_CUPONES, cuponJson(codigo, tipo, valor), propietario.token(), tienda)
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("id").asText());
    }

    private void rechazado(Map<String, Object> cupon, String codigoRegla) throws Exception {
        postJson(Rutas.PANEL_CUPONES, cupon, propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value(codigoRegla));
    }

    @Test
    void crearNormalizaElCodigoAMayusculas() throws Exception {
        JsonNode datos = cuerpo(postJson(Rutas.PANEL_CUPONES, cuponJson(" beb-10 ", "PORCENTAJE", 10),
                propietario.token(), tienda).andExpect(status().isCreated())).get("datos");

        assertThat(datos.get("codigo").asText()).isEqualTo("BEB-10");
        assertThat(datos.get("esta_activo").asBoolean()).isTrue();
        assertThat(datos.get("cantidad_usos_actual").asInt()).isZero();
    }

    @Test
    void codigoRepetidoEnLaMismaTiendaEs422YEnOtraTiendaSeAcepta() throws Exception {
        crearCupon("VERANO", "MONTO_FIJO", 10);

        postJson(Rutas.PANEL_CUPONES, cuponJson("verano", "MONTO_FIJO", 5), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CUP-001"));

        UUID otra = crearTienda(propietario.id());
        postJson(Rutas.PANEL_CUPONES, cuponJson("VERANO", "MONTO_FIJO", 5), propietario.token(), otra)
                .andExpect(status().isCreated());
    }

    @Test
    void valoresInvalidosDanCUP002YLasFechasCUP009() throws Exception {
        rechazado(cuponJson("PCT-0", "PORCENTAJE", 0), "CUP-002");
        rechazado(cuponJson("PCT-101", "PORCENTAJE", 101), "CUP-002");
        rechazado(cuponJson("FIJO-0", "MONTO_FIJO", 0), "CUP-002");
        rechazado(cuponJson("ENVIO-5", "ENVIO_GRATIS", 5), "CUP-002");

        Map<String, Object> fechas = cuponJson("FECHAS-1", "MONTO_FIJO", 5);
        fechas.put("fecha_inicio", "2026-10-10T00:00:00Z");
        fechas.put("fecha_fin", "2026-10-10T00:00:00Z");
        rechazado(fechas, "CUP-009");
    }

    @Test
    void gestorDeCatalogoNoGestionaCupones() throws Exception {
        Sesion gestor = nuevaSesion();
        agregarPersonal(tienda, gestor.id(), "GESTOR_CATALOGO");

        getConToken(Rutas.PANEL_CUPONES, gestor.token(), tienda).andExpect(status().isForbidden());
        postJson(Rutas.PANEL_CUPONES, cuponJson("GESTOR-1", "MONTO_FIJO", 5), gestor.token(), tienda)
                .andExpect(status().isForbidden());
    }

    @Test
    void cuponDeOtraTiendaResponde404() throws Exception {
        UUID cupon = crearCupon("PROPIO", "ENVIO_GRATIS", 0);
        UUID otra = crearTienda(propietario.id());

        getConToken(Rutas.panelCupon(cupon), propietario.token(), otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CUP-404"));
    }

    @Test
    void conUsosNoCambiaTipoNiValorPeroSiOtrosCampos() throws Exception {
        UUID cupon = crearCupon("CONUSOS", "PORCENTAJE", 10);
        fijarUsos(cupon, 1);

        putJson(Rutas.panelCupon(cupon), cuponJson("CONUSOS", "PORCENTAJE", 15), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CUP-008"));

        putJson(Rutas.panelCupon(cupon), cuponJson("CONUSOS", "PORCENTAJE", 10), propietario.token(), tienda)
                .andExpect(status().isOk());
    }

    @Test
    void eliminarEsLogicoYElCuponDejaDeVerse() throws Exception {
        UUID cupon = crearCupon("BORRAR", "ENVIO_GRATIS", 0);

        delete(Rutas.panelCupon(cupon), propietario.token(), tienda).andExpect(status().isOk());

        getConToken(Rutas.panelCupon(cupon), propietario.token(), tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CUP-404"));
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM \"Cupones\" WHERE \"Id\" = ? AND \"Eliminado\" = TRUE", Integer.class, cupon);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void listarFiltraPorTextoYPorActivo() throws Exception {
        crearCupon("AAA-ACTIVO", "MONTO_FIJO", 5);
        UUID pausado = crearCupon("AAA-PAUSA", "MONTO_FIJO", 5);
        crearCupon("ZZZ-OTRO", "MONTO_FIJO", 5);
        jdbc.update("UPDATE \"Cupones\" SET \"EstaActivo\" = FALSE WHERE \"Id\" = ?", pausado);

        JsonNode porTexto = cuerpo(getConToken(Rutas.PANEL_CUPONES + "?texto=aaa", propietario.token(), tienda)
                .andExpect(status().isOk())).get("datos").get("datos");
        assertThat(porTexto).hasSize(2);

        JsonNode soloActivos = cuerpo(getConToken(Rutas.PANEL_CUPONES + "?texto=aaa&activo=true", propietario.token(), tienda)
                .andExpect(status().isOk())).get("datos").get("datos");
        assertThat(soloActivos).hasSize(1);
        assertThat(soloActivos.get(0).get("codigo").asText()).isEqualTo("AAA-ACTIVO");
    }
}
