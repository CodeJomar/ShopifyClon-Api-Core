package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.usuario.evento.CodigoSegundoFactorEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CuentaBloqueadaEvento;
import shopify.api.core.soporte.Rutas;

/** A-007: revocación de sesiones, bloqueo por intentos fallidos, contraseñas comunes y verificación en dos pasos. */
class SeguridadCuentaTest extends PruebaUsuarioBase {

    private static final String LOGIN_VERIFICAR = "/auth/login/verificar";
    private static final String DESBLOQUEAR = "/auth/desbloquear";
    private static final String CERRAR_SESIONES = "/cuenta/cerrar-sesiones";
    private static final String DOS_PASOS = "/cuenta/dos-pasos";

    // ---- revocación de sesiones ----

    @Test
    void cambiarLaContrasenaInvalidaElTokenAnteriorYElNuevoLoginFunciona() throws Exception {
        String correo = nuevoCorreo();
        String tokenViejo = cuentaActivaConToken(correo);

        putJson(Rutas.CUENTA_CONTRASENA, Map.of("contrasena_actual", CLAVE, "contrasena_nueva", "Nueva98765"), tokenViejo, null)
                .andExpect(status().isOk());

        getConToken(Rutas.CUENTA_PERFIL, tokenViejo, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));

        String tokenNuevo = login(correo, "Nueva98765").get("datos").get("token").asText();
        getConToken(Rutas.CUENTA_PERFIL, tokenNuevo, null).andExpect(status().isOk());
    }

    @Test
    void cerrarSesionesInvalidaElTokenActual() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);

        postJson(CERRAR_SESIONES, null, token, null).andExpect(status().isOk());

        getConToken(Rutas.CUENTA_PERFIL, token, null).andExpect(status().isUnauthorized());
        String otro = login(correo, CLAVE).get("datos").get("token").asText();
        getConToken(Rutas.CUENTA_PERFIL, otro, null).andExpect(status().isOk());
    }

    @Test
    void restablecerLaClaveTambienCierraLasSesiones() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);
        postJson(Rutas.AUTH_RECUPERAR_SOLICITAR, Map.of("correo_electronico", correo), null, null).andExpect(status().isOk());

        postJson(Rutas.AUTH_RECUPERAR_CONFIRMAR, Map.of("correo_electronico", correo,
                "codigo", ultimoCodigoRecuperacion(correo), "contrasena_nueva", "Nueva98765"), null, null)
                .andExpect(status().isOk());

        getConToken(Rutas.CUENTA_PERFIL, token, null).andExpect(status().isUnauthorized());
    }

    // ---- bloqueo por intentos fallidos ----

    @Test
    void cincoFallosBloqueanLaCuentaYElCodigoDelCorreoLaDesbloquea() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        for (int i = 0; i < 4; i++) {
            intentar(correo, "Incorrecta1").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("USR-004"));
        }
        intentar(correo, "Incorrecta1").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USR-014"));

        // Bloqueada: ni la contraseña correcta entra
        intentar(correo, CLAVE).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("USR-014"));

        postJson(DESBLOQUEAR, Map.of("correo_electronico", correo, "codigo", codigoDeDesbloqueo(correo)), null, null)
                .andExpect(status().isOk());

        intentar(correo, CLAVE).andExpect(status().isOk()).andExpect(jsonPath("$.datos.token").exists());
    }

    @Test
    void unLoginCorrectoReiniciaElContadorDeFallos() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        for (int i = 0; i < 4; i++) {
            intentar(correo, "Incorrecta1").andExpect(status().isUnauthorized());
        }
        intentar(correo, CLAVE).andExpect(status().isOk());

        // Tras entrar bien, otros 4 fallos no bloquean
        for (int i = 0; i < 4; i++) {
            intentar(correo, "Incorrecta1").andExpect(status().isUnauthorized());
        }
        Integer fallos = jdbc.queryForObject(
                "SELECT \"IntentosLoginFallidos\" FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", Integer.class, correo);
        assertThat(fallos).isEqualTo(4);
    }

    @Test
    void unCodigoDeDesbloqueoIncorrectoNoDesbloquea() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);
        for (int i = 0; i < 5; i++) {
            intentar(correo, "Incorrecta1");
        }

        postJson(DESBLOQUEAR, Map.of("correo_electronico", correo,
                "codigo", codigoIncorrectoDistintoDe(codigoDeDesbloqueo(correo))), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));

        intentar(correo, CLAVE).andExpect(status().isConflict());
    }

    // ---- contraseñas comunes ----

    @Test
    void elRegistroRechazaUnaContrasenaComun() throws Exception {
        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(nuevoCorreo(), "Password123"), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-015"));
    }

    @Test
    void laContrasenaNoPuedeContenerElCorreo() throws Exception {
        String correo = "maria" + sufijo() + "@prueba.test";
        String usuario = correo.substring(0, correo.indexOf('@'));

        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(correo, usuario + "A1"), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-015"));
    }

    @Test
    void cambiarAUnaContrasenaComunSeRechazaYNoCierraLaSesion() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        putJson(Rutas.CUENTA_CONTRASENA, Map.of("contrasena_actual", CLAVE, "contrasena_nueva", "Qwerty123"), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-015"));

        getConToken(Rutas.CUENTA_PERFIL, token, null).andExpect(status().isOk());
    }

    // ---- verificación en dos pasos ----

    @Test
    void conDosPasosElLoginPideCodigoYEntregaElTokenAlVerificarlo() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);
        putJson(DOS_PASOS, Map.of("activo", true, "contrasena_actual", CLAVE), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.dos_pasos_activo").value(true));

        JsonNode paso1 = cuerpo(intentar(correo, CLAVE).andExpect(status().isOk()));
        assertThat(paso1.get("datos").get("requiere_segundo_factor").asBoolean()).isTrue();
        assertThat(paso1.get("datos").has("token")).isFalse();

        JsonNode paso2 = cuerpo(postJson(LOGIN_VERIFICAR,
                Map.of("correo_electronico", correo, "codigo", codigoDeSegundoFactor(correo)), null, null)
                .andExpect(status().isOk()));
        String tokenFinal = paso2.get("datos").get("token").asText();
        getConToken(Rutas.CUENTA_PERFIL, tokenFinal, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.dos_pasos_activo").value(true));
    }

    @Test
    void unCodigoDeSegundoFactorIncorrectoNoEntregaToken() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);
        putJson(DOS_PASOS, Map.of("activo", true, "contrasena_actual", CLAVE), token, null).andExpect(status().isOk());
        intentar(correo, CLAVE).andExpect(status().isOk());

        postJson(LOGIN_VERIFICAR, Map.of("correo_electronico", correo,
                "codigo", codigoIncorrectoDistintoDe(codigoDeSegundoFactor(correo))), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }

    @Test
    void sinHaberPasadoPorLaContrasenaNoHayCodigoQueCanjear() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        postJson(LOGIN_VERIFICAR, Map.of("correo_electronico", correo, "codigo", "123456"), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }

    @Test
    void activarDosPasosExigeLaContrasenaActualYSePuedeDesactivar() throws Exception {
        String correo = nuevoCorreo();
        String token = cuentaActivaConToken(correo);

        putJson(DOS_PASOS, Map.of("activo", true, "contrasena_actual", "Equivocada1"), token, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-010"));

        putJson(DOS_PASOS, Map.of("activo", true, "contrasena_actual", CLAVE), token, null).andExpect(status().isOk());
        putJson(DOS_PASOS, Map.of("activo", false, "contrasena_actual", CLAVE), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.dos_pasos_activo").value(false));

        JsonNode entrada = login(correo, CLAVE);
        assertThat(entrada.get("datos").get("requiere_segundo_factor").asBoolean()).isFalse();
        assertThat(entrada.get("datos").get("token").asText()).isNotBlank();
    }

    // ---- ayudas ----

    private org.springframework.test.web.servlet.ResultActions intentar(String correo, String contrasena) throws Exception {
        return postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", contrasena), null, null);
    }

    private String codigoDeDesbloqueo(String correo) {
        return eventos.stream(CuentaBloqueadaEvento.class)
                .filter(e -> e.correo().equalsIgnoreCase(correo))
                .reduce((primero, segundo) -> segundo)
                .map(CuentaBloqueadaEvento::codigo)
                .orElseThrow(() -> new AssertionError("No se emitió código de desbloqueo para " + correo));
    }

    private String codigoDeSegundoFactor(String correo) {
        return eventos.stream(CodigoSegundoFactorEmitidoEvento.class)
                .filter(e -> e.correo().equalsIgnoreCase(correo))
                .reduce((primero, segundo) -> segundo)
                .map(CodigoSegundoFactorEmitidoEvento::codigo)
                .orElseThrow(() -> new AssertionError("No se emitió código de segundo factor para " + correo));
    }
}
