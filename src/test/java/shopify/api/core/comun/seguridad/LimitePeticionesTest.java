package shopify.api.core.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * T-022, pendiente 4: con el límite de peticiones encendido, la 31.ª creación de carrito en un minuto responde 429.
 * Va en su propia clase porque el contexto de la prueba cambia la propiedad (D-023 la apaga en el resto).
 */
@TestPropertySource(properties = "app.limite-peticiones.habilitado=true")
class LimitePeticionesTest extends PruebaIntegracionBase {

    private String carrito;

    @BeforeEach
    void preparar() {
        UUID tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        carrito = Rutas.vitrinaCarrito(subdominio);
    }

    @Test
    void laTreintaYUnoaCreacionDeCarritoEnUnMinutoResponde429() throws Exception {
        for (int i = 0; i < 30; i++) {
            mvc.perform(post(carrito)).andExpect(status().isCreated());
        }

        mvc.perform(post(carrito))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("GEN-429"));
        assertThat(carrito).isNotBlank();
    }
}
