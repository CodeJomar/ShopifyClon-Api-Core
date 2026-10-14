package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class CuentaTest extends PruebaUsuarioBase {

    @Test
    void perfilSinTokenResponde401() throws Exception {
        getConToken(Rutas.CUENTA_PERFIL, null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));
    }

    @Test
    void actualizarPerfilCambiaNombresYTelefono() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        putJson(Rutas.CUENTA_PERFIL, Map.of("nombres", "María José", "apellidos", "Quispe", "telefono", "+51 999 888 777"),
                token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombres").value("María José"))
                .andExpect(jsonPath("$.datos.telefono").value("+51 999 888 777"));

        getConToken(Rutas.CUENTA_PERFIL, token, null)
                .andExpect(jsonPath("$.datos.apellidos").value("Quispe"));
    }

    @Test
    void telefonoInvalidoResponde422() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        putJson(Rutas.CUENTA_PERFIL, Map.of("nombres", "Ana", "apellidos", "Prueba", "telefono", "abc"), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.telefono").exists());
    }

    @Test
    void elPerfilNoPermiteEscribirLaBanderaDeAdministrador() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);
        Map<String, Object> cuerpo = new HashMap<>(Map.of("nombres", "Ana", "apellidos", "Prueba"));
        cuerpo.put("es_administrador_plataforma", true);

        putJson(Rutas.CUENTA_PERFIL, cuerpo, token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.es_administrador_plataforma").value(false));

        Boolean admin = jdbc.queryForObject(
                "SELECT \"EsAdministradorPlataforma\" FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", Boolean.class, correo);
        assertThat(admin).isFalse();
    }

    @Test
    void cambiarContrasenaValidaLaActualYLaNuevaDebeSerDistinta() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);

        putJson(Rutas.CUENTA_CONTRASENA, Map.of("contrasena_actual", "Equivocada1", "contrasena_nueva", "Nueva98765"), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-010"));

        putJson(Rutas.CUENTA_CONTRASENA, Map.of("contrasena_actual", CLAVE, "contrasena_nueva", CLAVE), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-010"));

        putJson(Rutas.CUENTA_CONTRASENA, Map.of("contrasena_actual", CLAVE, "contrasena_nueva", "Nueva98765"), token, null)
                .andExpect(status().isOk());

        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", "Nueva98765"), null, null)
                .andExpect(status().isOk());
        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", CLAVE), null, null)
                .andExpect(status().isUnauthorized());
    }
}
