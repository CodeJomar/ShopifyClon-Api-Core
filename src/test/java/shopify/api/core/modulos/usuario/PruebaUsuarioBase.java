package shopify.api.core.modulos.usuario;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CodigoRecuperacionEmitidoEvento;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Utilidades para las pruebas del módulo usuario; los códigos en claro se leen de los eventos publicados. */
@RecordApplicationEvents
abstract class PruebaUsuarioBase extends PruebaIntegracionBase {

    protected static final String CLAVE = "Clave12345";

    @Autowired
    protected ApplicationEvents eventos;

    protected String nuevoCorreo() {
        return "usr-" + sufijo() + "@prueba.test";
    }

    protected Map<String, Object> cuerpoRegistro(String correo, String contrasena) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo_electronico", correo);
        cuerpo.put("contrasena", contrasena);
        cuerpo.put("nombres", "Ana");
        cuerpo.put("apellidos", "Prueba");
        return cuerpo;
    }

    /** Registra la cuenta y devuelve su id. */
    protected UUID registrar(String correo) throws Exception {
        JsonNode cuerpo = cuerpo(postJson(Rutas.AUTH_REGISTRO, cuerpoRegistro(correo, CLAVE), null, null)
                .andExpect(status().isCreated()));
        return UUID.fromString(cuerpo.get("datos").get("id").asText());
    }

    protected String ultimoCodigoActivacion(String correo) {
        return eventos.stream(CodigoActivacionEmitidoEvento.class)
                .filter(e -> e.correo().equalsIgnoreCase(correo))
                .reduce((primero, segundo) -> segundo)
                .map(CodigoActivacionEmitidoEvento::codigo)
                .orElseThrow(() -> new AssertionError("No se emitió código de activación para " + correo));
    }

    protected String ultimoCodigoRecuperacion(String correo) {
        return eventos.stream(CodigoRecuperacionEmitidoEvento.class)
                .filter(e -> e.correo().equalsIgnoreCase(correo))
                .reduce((primero, segundo) -> segundo)
                .map(CodigoRecuperacionEmitidoEvento::codigo)
                .orElseThrow(() -> new AssertionError("No se emitió código de recuperación para " + correo));
    }

    protected void activar(String correo, String codigo) throws Exception {
        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", codigo), null, null);
    }

    /** Registra y activa una cuenta; devuelve el token de su login. */
    protected String cuentaActivaConToken(String correo) throws Exception {
        registrar(correo);
        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", ultimoCodigoActivacion(correo)),
                null, null).andExpect(status().isOk());
        return login(correo, CLAVE).get("datos").get("token").asText();
    }

    protected JsonNode login(String correo, String contrasena) throws Exception {
        return cuerpo(postJson(Rutas.AUTH_LOGIN,
                Map.of("correo_electronico", correo, "contrasena", contrasena), null, null));
    }

    protected String codigoIncorrectoDistintoDe(String codigo) {
        return codigo.equals("111111") ? "222222" : "111111";
    }
}
