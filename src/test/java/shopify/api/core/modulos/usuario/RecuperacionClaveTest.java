package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class RecuperacionClaveTest extends PruebaUsuarioBase {

    private static final String CLAVE_NUEVA = "Nueva98765";

    @Test
    void solicitarRespondeIgualExistaONoElCorreo() throws Exception {
        String existente = nuevoCorreo();
        cuentaActivaConToken(existente);

        String mensajeExistente = cuerpo(postJson(Rutas.AUTH_RECUPERAR_SOLICITAR,
                Map.of("correo_electronico", existente), null, null).andExpect(status().isOk())).get("mensaje").asText();
        String mensajeInexistente = cuerpo(postJson(Rutas.AUTH_RECUPERAR_SOLICITAR,
                Map.of("correo_electronico", nuevoCorreo()), null, null).andExpect(status().isOk())).get("mensaje").asText();

        assertThat(mensajeInexistente).isEqualTo(mensajeExistente);
    }

    @Test
    void recuperarClaveCompletoCambiaLaContrasena() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null)
                .andExpect(status().isOk());
        String codigo = ultimoCodigoRecuperacion(correo);

        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR,
                Map.of("correo_electronico", correo, "codigo", codigo, "contrasena_nueva", CLAVE_NUEVA), null, null)
                .andExpect(status().isOk());

        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", CLAVE_NUEVA), null, null)
                .andExpect(status().isOk());
        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", CLAVE), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USR-004"));

        // el código es de un solo uso
        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR,
                Map.of("correo_electronico", correo, "codigo", codigo, "contrasena_nueva", "Otra123456"), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }

    @Test
    void confirmarConElCodigoDeActivacionNoSirve() throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);
        String codigoActivacion = ultimoCodigoActivacion(correo);
        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null)
                .andExpect(status().isOk());
        String codigoRecuperacion = ultimoCodigoRecuperacion(correo);
        assertThat(codigoActivacion).isNotEqualTo(codigoRecuperacion);

        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR,
                Map.of("correo_electronico", correo, "codigo", codigoActivacion, "contrasena_nueva", CLAVE_NUEVA), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }

    @Test
    void restablecerNoActivaUnaCuentaSinActivar() throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);
        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null)
                .andExpect(status().isOk());

        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR, Map.of("correo_electronico", correo,
                "codigo", ultimoCodigoRecuperacion(correo), "contrasena_nueva", CLAVE_NUEVA), null, null)
                .andExpect(status().isOk());

        Boolean activo = jdbc.queryForObject(
                "SELECT \"EstaActivo\" FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", Boolean.class, correo);
        assertThat(activo).isFalse();
    }

    @Test
    void confirmarConContrasenaDebilResponde422() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);
        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null);

        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR, Map.of("correo_electronico", correo,
                "codigo", ultimoCodigoRecuperacion(correo), "contrasena_nueva", "corta"), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.contrasena_nueva").exists());
    }

    @Test
    void confirmarParaCorreoInexistenteResponde422SinRevelarlo() throws Exception {
        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR, Map.of("correo_electronico", nuevoCorreo(),
                "codigo", "123456", "contrasena_nueva", CLAVE_NUEVA), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }
}
