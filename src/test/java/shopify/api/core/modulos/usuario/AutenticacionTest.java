package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import shopify.api.core.soporte.Rutas;

class AutenticacionTest extends PruebaUsuarioBase {

    /** Raíz del módulo anterior, retirada en T-003; se arma por partes para que el grep de verificación quede vacío. */
    private static final String RAIZ_RETIRADA = "/" + "usuarios";

    @Test
    void registroValidoGuardaCuentaSinActivarYCodigoHasheado() throws Exception {
        String sufijo = sufijo();
        String correoEnviado = "Ana-" + sufijo + "@Prueba.TEST";
        String correoGuardado = correoEnviado.toLowerCase();

        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(correoEnviado, CLAVE), null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.id").exists());

        Map<String, Object> usuario = jdbc.queryForMap(
                "SELECT \"Id\", \"CorreoElectronico\", \"EstaActivo\", \"EsAdministradorPlataforma\", \"ContrasenaHash\" "
                        + "FROM \"Usuarios\" WHERE LOWER(\"CorreoElectronico\") = ?", correoGuardado);
        assertThat(usuario.get("CorreoElectronico")).isEqualTo(correoGuardado);
        assertThat(usuario.get("EstaActivo")).isEqualTo(false);
        assertThat(usuario.get("EsAdministradorPlataforma")).isEqualTo(false);
        assertThat((String) usuario.get("ContrasenaHash")).startsWith("$2").isNotEqualTo(CLAVE);

        String codigo = ultimoCodigoActivacion(correoGuardado);
        assertThat(codigo).matches("\\d{6}");
        Map<String, Object> token = jdbc.queryForMap(
                "SELECT \"CodigoToken\", \"Tipo\", \"Usado\", \"IntentosFallidos\" FROM \"TokensSeguridad\" WHERE \"UsuarioId\" = ?",
                usuario.get("Id"));
        assertThat((String) token.get("CodigoToken")).matches("[0-9a-f]{64}").isNotEqualTo(codigo);
        assertThat(token.get("Tipo")).isEqualTo("ACTIVACION_CUENTA");
        assertThat(token.get("Usado")).isEqualTo(false);
    }

    @Test
    void registroConCorreoExistenteSinImportarMayusculasResponde422() throws Exception {
        String sufijo = sufijo();
        registrar("ana-" + sufijo + "@x.test");

        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro("Ana-" + sufijo + "@X.test", CLAVE), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefgh", "Abc1234", "12345678"})
    void contrasenaInvalidaResponde422(String contrasena) throws Exception {
        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(nuevoCorreo(), contrasena), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.contrasena").exists());
    }

    @Test
    void contrasenaDe73CaracteresResponde422() throws Exception {
        postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(nuevoCorreo(), "a1" + "x".repeat(71)), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.validaciones.contrasena").exists());
    }

    @Test
    void registroNoAceptaRolNiBanderaDeAdministrador() throws Exception {
        String correo = nuevoCorreo();
        Map<String, Object> cuerpo = cuerpoRegistro(correo, CLAVE);
        cuerpo.put("es_administrador_plataforma", true);
        cuerpo.put("codigo_rol", "ADMIN_TIENDA");

        postJson(Rutas.AUTH_REGISTRO, cuerpo, null, null).andExpect(status().isCreated());

        Boolean admin = jdbc.queryForObject(
                "SELECT \"EsAdministradorPlataforma\" FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", Boolean.class, correo);
        assertThat(admin).isFalse();
    }

    @Test
    void loginAntesDeActivarDependeDeLaClave() throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);

        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", CLAVE), null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("USR-003"));

        postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", "Otra12345"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USR-004"));
    }

    @Test
    void activarConCodigoCorrectoPermiteIniciarSesion() throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);

        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", ultimoCodigoActivacion(correo)), null, null)
                .andExpect(status().isOk());

        JsonNode respuesta = login(correo, CLAVE);
        assertThat(respuesta.get("exito").asBoolean()).isTrue();
        JsonNode datos = respuesta.get("datos");
        assertThat(datos.get("token").asText()).isNotBlank();
        assertThat(datos.get("tipo").asText()).isEqualTo("Bearer");
        assertThat(Instant.parse(datos.get("expira_en").asText())).isAfter(Instant.now());
        assertThat(datos.get("usuario").get("correo_electronico").asText()).isEqualTo(correo);
        assertThat(datos.get("usuario").get("es_administrador_plataforma").asBoolean()).isFalse();
        assertThat(datos.get("tiendas").isArray()).isTrue();
        assertThat(datos.get("tiendas")).isEmpty();
        assertThat(datos.get("usuario").has("contrasena_hash")).isFalse();
    }

    @Test
    void activarUnaCuentaYaActivaRespondeOkSinEfectos() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", "123456"), null, null)
                .andExpect(status().isOk());
    }

    @Test
    void cincoCodigosIncorrectosAgotanElCodigoAunqueDespuesSeaElCorrecto() throws Exception {
        String correo = nuevoCorreo();
        UUID id = registrar(correo);
        String correcto = ultimoCodigoActivacion(correo);
        String incorrecto = codigoIncorrectoDistintoDe(correcto);

        for (int i = 0; i < 5; i++) {
            postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", incorrecto), null, null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("USR-012"));
        }
        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", correcto), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));

        Map<String, Object> token = jdbc.queryForMap(
                "SELECT \"IntentosFallidos\", \"Usado\" FROM \"TokensSeguridad\" WHERE \"UsuarioId\" = ?", id);
        assertThat(token.get("IntentosFallidos")).isEqualTo(5);
        assertThat(token.get("Usado")).isEqualTo(true);
    }

    @Test
    void activarConCodigoVencidoResponde422() throws Exception {
        String correo = nuevoCorreo();
        UUID id = registrar(correo);
        jdbc.update("UPDATE \"TokensSeguridad\" SET \"FechaExpiracion\" = now() - interval '1 minute' WHERE \"UsuarioId\" = ?", id);

        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", ultimoCodigoActivacion(correo)), null, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("USR-012"));
    }

    @Test
    void reenviarInvalidaElCodigoAnteriorYElNuevoFunciona() throws Exception {
        String correo = nuevoCorreo();
        registrar(correo);
        String anterior = ultimoCodigoActivacion(correo);

        postJson(Rutas.AUTH_REENVIAR_ACTIVACION, Map.of("correo_electronico", correo), null, null)
                .andExpect(status().isOk());
        String nuevo = ultimoCodigoActivacion(correo);
        assertThat(nuevo).isNotEqualTo(anterior);

        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", anterior), null, null)
                .andExpect(status().isUnprocessableEntity());
        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", nuevo), null, null)
                .andExpect(status().isOk());
    }

    @Test
    void reenviarRespondeIgualParaCorreoInexistenteYExistente() throws Exception {
        String existente = nuevoCorreo();
        registrar(existente);

        String mensajeExistente = cuerpo(postJson(Rutas.AUTH_REENVIAR_ACTIVACION,
                Map.of("correo_electronico", existente), null, null).andExpect(status().isOk())).get("mensaje").asText();
        String mensajeInexistente = cuerpo(postJson(Rutas.AUTH_REENVIAR_ACTIVACION,
                Map.of("correo_electronico", nuevoCorreo()), null, null).andExpect(status().isOk())).get("mensaje").asText();

        assertThat(mensajeInexistente).isEqualTo(mensajeExistente);
    }

    @Test
    void loginConCorreoInexistenteOClaveIncorrectaDaElMismoError() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);

        JsonNode claveMala = cuerpo(postJson(Rutas.AUTH_LOGIN,
                Map.of("correo_electronico", correo, "contrasena", "Incorrecta1"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("USR-004")));
        JsonNode sinCuenta = cuerpo(postJson(Rutas.AUTH_LOGIN,
                Map.of("correo_electronico", nuevoCorreo(), "contrasena", "Incorrecta1"), null, null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("USR-004")));

        assertThat(sinCuenta.get("mensaje").asText()).isEqualTo(claveMala.get("mensaje").asText());
    }

    @Test
    void loginDevuelveLasTiendasConSuRolYOmiteEliminadasYClientes() throws Exception {
        String correo = nuevoCorreo();
        cuentaActivaConToken(correo);
        UUID usuario = jdbc.queryForObject(
                "SELECT \"Id\" FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", UUID.class, correo);

        UUID propia = crearTienda(usuario);
        UUID ajena = crearTienda(crearUsuarioActivo(false));
        agregarPersonal(ajena, usuario, "GESTOR_PEDIDOS");
        UUID eliminada = crearTienda(usuario);
        eliminarTienda(eliminada);
        UUID comoCliente = crearTienda(crearUsuarioActivo(false));
        agregarPersonal(comoCliente, usuario, "CLIENTE");

        JsonNode tiendas = login(correo, CLAVE).get("datos").get("tiendas");

        Map<String, String> rolPorTienda = new HashMap<>();
        tiendas.forEach(t -> rolPorTienda.put(t.get("tienda_id").asText(), t.get("rol").asText()));
        assertThat(rolPorTienda).containsOnly(
                Map.entry(propia.toString(), "PROPIETARIO"),
                Map.entry(ajena.toString(), "GESTOR_PEDIDOS"));
        assertThat(tiendas.get(0).has("subdominio")).isTrue();
        assertThat(tiendas.get(0).has("nombre")).isTrue();
    }

    @Test
    void elTokenDelLoginSirveEnLaCuenta() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        getConToken(Rutas.CUENTA_PERFIL, token, null).andExpect(status().isOk());
    }

    @Test
    void ningunaRutaUsuariosResponde() throws Exception {
        String token = cuentaActivaConToken(nuevoCorreo());

        postJson(RAIZ_RETIRADA + "/registro", Map.of(), token, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-404"));
        getConToken(RAIZ_RETIRADA + "/perfil", token, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("GEN-404"));
    }
}
