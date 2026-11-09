package shopify.api.core.comun.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Con {@code app.laboratorio.habilitado=false} el laboratorio desaparece (producción). */
@TestPropertySource(properties = "app.laboratorio.habilitado=false")
class LaboratorioDeshabilitadoTest extends PruebaIntegracionBase {

    @Test
    void conLaBanderaApagadaLasPaginasDan404() throws Exception {
        mvc.perform(get("/laboratorio/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/laboratorio/assets/lab.js")).andExpect(status().isNotFound());
    }
}
