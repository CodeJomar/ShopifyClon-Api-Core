package shopify.api.core.comun.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** El laboratorio de endpoints se sirve sin sesión y trae todas sus páginas. */
class LaboratorioTest extends PruebaIntegracionBase {

    private static final List<String> PAGINAS = List.of(
            "index", "cuenta", "tienda", "suscripcion", "plataforma", "catalogo", "inventario", "cupones", "tarifas",
            "carrito", "checkout", "ordenes", "pagos", "envios", "rutas", "seguimiento", "metricas");

    @Test
    void cadaPaginaSeSirveSinSesion() throws Exception {
        for (String pagina : PAGINAS) {
            var cuerpo = mvc.perform(get("/laboratorio/" + pagina + ".html"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(cuerpo).as(pagina).contains("assets/lab.js").contains("Lab.pagina");
        }
    }

    @Test
    void sirveLosRecursosCompartidos() throws Exception {
        mvc.perform(get("/laboratorio/assets/lab.js")).andExpect(status().isOk());
        mvc.perform(get("/laboratorio/assets/recetas.js")).andExpect(status().isOk());
        mvc.perform(get("/laboratorio/assets/lab.css")).andExpect(status().isOk());
    }

    @Test
    void laRaizRedirigeALaPortada() throws Exception {
        mvc.perform(get("/laboratorio")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/laboratorio/index.html")));
    }

    @Test
    void unaPaginaQueNoExisteDa404() throws Exception {
        mvc.perform(get("/laboratorio/no-existe.html")).andExpect(status().isNotFound());
    }
}
