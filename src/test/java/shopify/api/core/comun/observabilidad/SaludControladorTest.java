package shopify.api.core.comun.observabilidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** El health responde sin sesión y sin cabeceras de tienda. */
class SaludControladorTest extends PruebaIntegracionBase {

    @Test
    void respondeUpSinSesion() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exito").value(true))
                .andExpect(jsonPath("$.datos.estado").value("UP"));
    }
}
