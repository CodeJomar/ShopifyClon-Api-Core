package shopify.api.core.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

class RespuestaErroresTest extends PruebaIntegracionBase {

    private String token;

    @BeforeEach
    void preparar() {
        UUID usuario = crearUsuarioActivo(false);
        token = tokenPara(usuario, "e@prueba.test");
    }

    @ParameterizedTest
    @CsvSource({
            "solicitud,400,PRU-400",
            "no-autenticado,401,PRU-401",
            "acceso,403,PRU-403",
            "no-encontrado,404,PRU-404",
            "conflicto,409,PRU-409",
            "regla,422,PRU-422"
    })
    void cadaExcepcionDeNegocioRespondeConSuEstadoYCodigo(String tipo, int estado, String codigo) throws Exception {
        getConToken(Rutas.PRUEBA_ERROR + tipo, token, null)
                .andExpect(status().is(estado))
                .andExpect(jsonPath("$.exito").value(false))
                .andExpect(jsonPath("$.codigo_estado").value(estado))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.ruta").value(Rutas.PRUEBA_ERROR + tipo));
    }

    @Test
    void elConflictoTraeDetalles() throws Exception {
        getConToken(Rutas.PRUEBA_ERROR + "conflicto", token, null)
                .andExpect(jsonPath("$.detalles.disponible").value(3))
                .andExpect(jsonPath("$.detalles.solicitado").value(5));
    }

    @Test
    void elErrorInesperadoNoExponeElMensajeInterno() throws Exception {
        var resultado = getConToken(Rutas.PRUEBA_ERROR + "interno", token, null)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("GEN-500"));

        String cuerpo = resultado.andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("detalle-interno-secreto");
        var datos = cuerpo(resultado);
        assertThat(datos.get("mensaje").asText()).endsWith(datos.get("id_correlacion").asText());
    }

    @Test
    void validacionVaciaResponde422ConCamposEnSnakeCase() throws Exception {
        postJson(Rutas.PRUEBA_VALIDAR, Map.of(), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.nombre_campo").exists());
    }

    @Test
    void jsonRotoResponde400() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post(Rutas.PRUEBA_VALIDAR)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VAL-002"));
    }

    @Test
    void rutaInexistenteAutenticadaResponde404() throws Exception {
        getConToken(Rutas.RUTA_INEXISTENTE, token, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-404"));
    }

    @Test
    void metodoNoSoportadoResponde405() throws Exception {
        postJson(Rutas.PRUEBA_PLATAFORMA, Map.of(), token, null)
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("GEN-405"));
    }

    @Test
    void elIdDeCorrelacionEntrantePasaARespuestaYEncabezado() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get(Rutas.PRUEBA_ERROR + "regla")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlacion-Id", "prueba-123456"))
                .andExpect(header().string("X-Correlacion-Id", "prueba-123456"))
                .andExpect(jsonPath("$.id_correlacion").value("prueba-123456"));
    }

    @Test
    void unIdDeCorrelacionInvalidoSeReemplazaPorUnUuid() throws Exception {
        var respuesta = mvc.perform(MockMvcRequestBuilders.get(Rutas.PRUEBA_ERROR + "regla")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlacion-Id", "x"))
                .andReturn().getResponse();

        assertThat(respuesta.getHeader("X-Correlacion-Id")).isNotEqualTo("x").hasSize(36);
    }

    @Test
    void elErrorDe401TambienLlevaIdDeCorrelacion() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.id_correlacion").exists());
    }
}
