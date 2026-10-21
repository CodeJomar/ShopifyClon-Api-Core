package shopify.api.core.comun.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * A-002: con el secreto del proxy configurado, el límite se cuenta por la IP de X-Forwarded-For solo si el secreto
 * coincide y la IP es válida. Cada prueba usa su propia IP de conexión porque el limitador vive en la aplicación.
 */
@TestPropertySource(properties = {
        "app.limite-peticiones.habilitado=true",
        "app.proxy.secreto=secreto-de-prueba-largo"})
class LimiteIpProxyTest extends PruebaIntegracionBase {

    private static final String SECRETO = "secreto-de-prueba-largo";

    private String carrito;

    @BeforeEach
    void preparar() {
        UUID tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        carrito = Rutas.vitrinaCarrito(subdominio);
    }

    @Test
    void conSecretoCorrectoDosIpsReenviadasTienenLimitesIndependientes() throws Exception {
        String conexion = "198.51.100.1";
        for (int i = 0; i < 30; i++) {
            pedido(conexion, "203.0.113.10", SECRETO).andExpect(status().isCreated());
        }
        pedido(conexion, "203.0.113.10", SECRETO)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("GEN-429"));

        pedido(conexion, "203.0.113.20", SECRETO).andExpect(status().isCreated());
    }

    @Test
    void conSecretoIncorrectoSeIgnoraXForwardedForYSeCuentaPorLaConexion() throws Exception {
        String conexion = "198.51.100.2";
        for (int i = 0; i < 30; i++) {
            pedido(conexion, "203.0.113." + (i + 1), "otro-secreto").andExpect(status().isCreated());
        }
        pedido(conexion, "203.0.113.99", "otro-secreto").andExpect(status().isTooManyRequests());
    }

    @Test
    void sinCabeceraDeSecretoSeIgnoraXForwardedForYSeCuentaPorLaConexion() throws Exception {
        String conexion = "198.51.100.3";
        for (int i = 0; i < 30; i++) {
            pedido(conexion, "203.0.113." + (i + 1), null).andExpect(status().isCreated());
        }
        pedido(conexion, "203.0.113.99", null).andExpect(status().isTooManyRequests());
    }

    @Test
    void conSecretoCorrectoUnaCabeceraQueNoEsIpNoSeUsaYSeCuentaPorLaConexion() throws Exception {
        String conexion = "198.51.100.4";
        for (int i = 0; i < 30; i++) {
            pedido(conexion, "no-es-ip-" + i, SECRETO).andExpect(status().isCreated());
        }
        pedido(conexion, "no-es-ip-99", SECRETO).andExpect(status().isTooManyRequests());
    }

    private ResultActions pedido(String conexion, String ipReenviada, String secreto) throws Exception {
        var peticion = post(carrito).with(r -> {
            r.setRemoteAddr(conexion);
            return r;
        }).header("X-Forwarded-For", ipReenviada);
        if (secreto != null) {
            peticion.header("X-Proxy-Secreto", secreto);
        }
        return mvc.perform(peticion);
    }
}
