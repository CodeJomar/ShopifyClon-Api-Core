package shopify.api.core.modulos.seguimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaConEnvios;

/** A-004: seguimiento público por número de guía, sin sesión y sin datos personales. */
class SeguimientoTest extends PruebaConEnvios {

    private String guiaDe(UUID envio) {
        return jdbc.queryForObject("SELECT \"NumeroGuia\" FROM \"Envios\" WHERE \"Id\" = ?", String.class, envio);
    }

    @Test
    void respondeSinSesionConElHistorialYSinDatosPersonales() throws Exception {
        UUID envio = envioCreado();
        postJson("/panel/envios/" + envio + "/despachar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + envio + "/no-entregado", Map.of("nota", "Cliente ausente"), gestorJwt, tienda)
                .andExpect(status().isOk());

        var respuesta = mvc.perform(get("/seguimiento/" + guiaDe(envio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.numero_guia").value(guiaDe(envio)))
                .andExpect(jsonPath("$.datos.estado").value("EN_TRANSITO"))
                .andExpect(jsonPath("$.datos.empresa_transporte").value("Coordinadora"))
                .andReturn().getResponse();

        JsonNode datos = json.readTree(respuesta.getContentAsString()).get("datos");
        List<String> estados = datos.get("eventos").findValuesAsText("estado");
        assertThat(estados).containsExactly("NO_ENTREGADO", "EN_TRANSITO", "CREADO");
        assertThat(datos.get("fecha_despacho").asText()).isNotBlank();

        String texto = respuesta.getContentAsString();
        assertThat(texto).doesNotContain("Ana Perez").doesNotContain("Siempre Viva").doesNotContain("999888777")
                .doesNotContain("comprador@prueba.test").doesNotContain("latitud");
        assertThat(datos.has("direccion")).isFalse();
        assertThat(datos.has("telefono")).isFalse();
    }

    @Test
    void conRutaMuestraAlChofer() throws Exception {
        UUID envio = envioCreado();
        postJson("/panel/rutas", Map.of("fecha", LocalDate.now().plusDays(1).toString(), "chofer", "Luis Ramos",
                "placa", "XYZ-987", "envio_ids", List.of(envio)), gestorJwt, tienda).andExpect(status().isCreated());

        mvc.perform(get("/seguimiento/" + guiaDe(envio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.chofer").value("Luis Ramos"));
    }

    @Test
    void unaGuiaInexistenteEsUn404Generico() throws Exception {
        mvc.perform(get("/seguimiento/OT-20000101-AAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENV-404"));
    }

    @Test
    void laEntregaConFotoApareceEnElSeguimiento() throws Exception {
        UUID envio = envioCreado();
        postJson("/panel/envios/" + envio + "/despachar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + envio + "/entregar", Map.of("foto_url", "https://img.test/e.jpg"), gestorJwt, tienda)
                .andExpect(status().isOk());

        mvc.perform(get("/seguimiento/" + guiaDe(envio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ENTREGADO"))
                .andExpect(jsonPath("$.datos.eventos[0].foto_url").value("https://img.test/e.jpg"))
                .andExpect(jsonPath("$.datos.fecha_entrega_real").exists());
    }
}
