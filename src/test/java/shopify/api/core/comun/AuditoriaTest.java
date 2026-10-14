package shopify.api.core.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

class AuditoriaTest extends PruebaIntegracionBase {

    private static final String ENTIDAD_ID = "00000000-0000-0000-0000-00000000a0d1";

    @Test
    void auditarDentroDeTransaccionInsertaLaFila() throws Exception {
        UUID usuario = crearUsuarioActivo(false);
        String token = tokenPara(usuario, "au@prueba.test");
        jdbc.update("DELETE FROM \"RegistroAuditoria\" WHERE \"EntidadId\" = ?::uuid", ENTIDAD_ID);

        var respuesta = mvc.perform(MockMvcRequestBuilders.post(Rutas.PRUEBA_AUDITAR)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Correlacion-Id", "auditoria-123456"))
                .andExpect(status().isOk());
        assertThat(respuesta).isNotNull();

        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT \"Accion\", \"UsuarioId\", \"IdCorrelacion\", \"Motivo\", \"Detalle\"::text AS detalle "
                        + "FROM \"RegistroAuditoria\" WHERE \"EntidadId\" = ?::uuid", ENTIDAD_ID);

        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.get(0);
        assertThat(fila.get("Accion")).isEqualTo("INVENTARIO_AJUSTADO");
        assertThat(fila.get("UsuarioId")).isEqualTo(usuario);
        assertThat(fila.get("IdCorrelacion")).isEqualTo("auditoria-123456");
        assertThat(fila.get("Motivo")).isEqualTo("motivo de prueba");

        var detalle = json.readTree((String) fila.get("detalle"));
        assertThat(detalle.get("antes").get("cantidad").asInt()).isEqualTo(5);
        assertThat(detalle.get("despues").get("cantidad").asInt()).isEqualTo(8);
    }

    @Test
    void auditarSinTransaccionFallaYNoInsertaNada() throws Exception {
        String token = tokenPara(crearUsuarioActivo(false), "as@prueba.test");
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\"", Integer.class);

        mvc.perform(MockMvcRequestBuilders.post(Rutas.PRUEBA_AUDITAR_SIN_TRANSACCION)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isInternalServerError());

        Integer despues = jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\"", Integer.class);
        assertThat(despues).isEqualTo(antes);
    }
}
