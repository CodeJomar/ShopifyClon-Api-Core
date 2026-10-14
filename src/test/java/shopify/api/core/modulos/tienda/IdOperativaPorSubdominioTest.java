package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.soporte.Rutas;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

class IdOperativaPorSubdominioTest extends PruebaTiendaBase {

    @Autowired
    private TiendaServicio tiendas;

    @Test
    void devuelveElIdSoloDeUnaTiendaOperativa() throws Exception {
        Sesion propietario = nuevaSesion();
        String subdominio = subdominioUnico();
        UUID tienda = crearTiendaPorApi(propietario, subdominio);

        assertThat(tiendas.idOperativaPorSubdominio(subdominio)).contains(tienda);
        assertThat(tiendas.idOperativaPorSubdominio(" " + subdominio.toUpperCase() + " ")).contains(tienda);

        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Prueba"), propietario.token(), tienda)
                .andExpect(status().isOk());
        assertThat(tiendas.idOperativaPorSubdominio(subdominio)).isEmpty();

        assertThat(tiendas.idOperativaPorSubdominio("no-existe-" + sufijo())).isEmpty();
        assertThat(tiendas.idOperativaPorSubdominio(null)).isEmpty();
    }
}
