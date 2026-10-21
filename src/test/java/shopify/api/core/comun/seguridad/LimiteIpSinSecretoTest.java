package shopify.api.core.comun.seguridad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * A-002: con {@code app.proxy.secreto} vacío nunca se confía en X-Forwarded-For, aunque la cabecera de secreto
 * llegue con un valor. El secreto vacío se fija explícitamente para que un .env local no altere la prueba.
 */
@TestPropertySource(properties = {
        "app.limite-peticiones.habilitado=true",
        "app.proxy.secreto="})
class LimiteIpSinSecretoTest extends PruebaIntegracionBase {

    private String carrito;

    @BeforeEach
    void preparar() {
        UUID tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        carrito = Rutas.vitrinaCarrito(subdominio);
    }

    @Test
    void conSecretoVacioSeIgnoraXForwardedForAunqueLaCabeceraDeSecretoLlegue() throws Exception {
        String conexion = "198.51.100.5";
        for (int i = 0; i < 30; i++) {
            mvc.perform(post(carrito).with(r -> {
                r.setRemoteAddr(conexion);
                return r;
            }).header("X-Forwarded-For", "203.0.113." + (i + 1)).header("X-Proxy-Secreto", "")).andExpect(status().isCreated());
        }
        mvc.perform(post(carrito).with(r -> {
            r.setRemoteAddr(conexion);
            return r;
        }).header("X-Forwarded-For", "203.0.113.99").header("X-Proxy-Secreto", "")).andExpect(status().isTooManyRequests());
    }
}
