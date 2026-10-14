package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class DireccionTest extends PruebaUsuarioBase {

    private Map<String, Object> direccion(String contacto, Boolean principal) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre_contacto", contacto);
        cuerpo.put("telefono", "999888777");
        cuerpo.put("direccion_linea1", "Av. Siempre Viva 742");
        cuerpo.put("ciudad", "Lima");
        cuerpo.put("estado_provincia", "Lima");
        cuerpo.put("codigo_postal", "15001");
        cuerpo.put("pais", "Perú");
        if (principal != null) {
            cuerpo.put("es_principal", principal);
        }
        return cuerpo;
    }

    private UUID crear(String token, String contacto, Boolean principal) throws Exception {
        JsonNode cuerpo = cuerpo(postJson(Rutas.CUENTA_DIRECCIONES, direccion(contacto, principal), token, null)
                .andExpect(status().isCreated()));
        return UUID.fromString(cuerpo.get("datos").get("id").asText());
    }

    private int principalesEnBase(String token) throws Exception {
        int cuenta = 0;
        for (JsonNode d : cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, token, null)).get("datos")) {
            if (d.get("es_principal").asBoolean()) {
                cuenta++;
            }
        }
        return cuenta;
    }

    @Test
    void laPrimeraDireccionEsPrincipalYUnaNuevaPrincipalLaReemplaza() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());
        UUID primera = crear(token, "Primera", null);
        UUID segunda = crear(token, "Segunda", false);

        JsonNode lista = cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, token, null)).get("datos");
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("id").asText()).isEqualTo(primera.toString());
        assertThat(lista.get(0).get("es_principal").asBoolean()).isTrue();
        assertThat(lista.get(1).get("es_principal").asBoolean()).isFalse();

        UUID tercera = crear(token, "Tercera", true);
        lista = cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, token, null)).get("datos");
        assertThat(lista.get(0).get("id").asText()).isEqualTo(tercera.toString());
        assertThat(principalesEnBase(token)).isEqualTo(1);
        assertThat(segunda).isNotNull();
    }

    @Test
    void marcarPrincipalDesmarcaLaAnterior() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());
        crear(token, "Primera", null);
        UUID segunda = crear(token, "Segunda", false);

        postJson(Rutas.cuentaDireccionPrincipal(segunda), Map.of(), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.es_principal").value(true));

        assertThat(principalesEnBase(token)).isEqualTo(1);
        JsonNode lista = cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, token, null)).get("datos");
        assertThat(lista.get(0).get("id").asText()).isEqualTo(segunda.toString());
    }

    @Test
    void eliminarLaPrincipalPromueveLaMasReciente() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());
        UUID primera = crear(token, "Primera", null);
        crear(token, "Segunda", false);
        UUID tercera = crear(token, "Tercera", false);

        delete(Rutas.cuentaDireccion(primera), token, null).andExpect(status().isOk());

        JsonNode lista = cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, token, null)).get("datos");
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("id").asText()).isEqualTo(tercera.toString());
        assertThat(lista.get(0).get("es_principal").asBoolean()).isTrue();
        assertThat(principalesEnBase(token)).isEqualTo(1);
        Boolean eliminada = jdbc.queryForObject(
                "SELECT \"Eliminado\" FROM \"DireccionesUsuario\" WHERE \"Id\" = ?", Boolean.class, primera);
        assertThat(eliminada).isTrue();
    }

    @Test
    void actualizarCambiaLosDatosSinTocarLaPrincipal() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());
        UUID id = crear(token, "Original", null);

        putJson(Rutas.cuentaDireccion(id), direccion("Editada", false), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombre_contacto").value("Editada"))
                .andExpect(jsonPath("$.datos.es_principal").value(true));
    }

    @Test
    void direccionDeOtroUsuarioResponde404() throws Exception {
        String dueno = cuentaActivaConToken(nuevoCorreo());
        String intruso = cuentaActivaConToken(nuevoCorreo());
        UUID ajena = crear(dueno, "Ajena", null);

        putJson(Rutas.cuentaDireccion(ajena), direccion("Robada", null), intruso, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("USR-404"));
        delete(Rutas.cuentaDireccion(ajena), intruso, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("USR-404"));
        postJson(Rutas.cuentaDireccionPrincipal(ajena), Map.of(), intruso, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("USR-404"));
        assertThat(cuerpo(getConToken(Rutas.CUENTA_DIRECCIONES, intruso, null)).get("datos")).isEmpty();
    }

    @Test
    void laDireccionVeintiunoResponde422() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());
        for (int i = 1; i <= 20; i++) {
            crear(token, "Dirección " + i, null);
        }

        postJson(Rutas.CUENTA_DIRECCIONES, direccion("Una más", null), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-011"));
    }

    @Test
    void camposObligatoriosSeValidan() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        postJson(Rutas.CUENTA_DIRECCIONES, Map.of("telefono", "123456"), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.nombre_contacto").exists())
                .andExpect(jsonPath("$.validaciones.direccion_linea1").exists())
                .andExpect(jsonPath("$.validaciones.pais").exists());
    }
}
