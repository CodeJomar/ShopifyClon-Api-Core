package shopify.api.core.modulos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** T-008 punto 4: el catálogo legado y TenantContextHolder ya no existen; /catalogo/** no responde. */
class RetiroLegadoCatalogoTest extends PruebaIntegracionBase {

    @Test
    void elModuloLegadoCatalogoNoExisteEnElCodigo() {
        assertThat(Files.exists(Path.of("src/main/java/shopify/api/core/modules/catalogo"))).isFalse();
    }

    @Test
    void tenantContextHolderYaNoExiste() {
        assertThatThrownBy(() -> Class.forName("shopify.api.core.comun.seguridad.TenantContextHolder"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void lasRutasDelCatalogoLegadoNoRespondenSinSesion() throws Exception {
        getConToken("/catalogo/productos", null, null).andExpect(status().isUnauthorized());
        getConToken("/catalogo/categorias", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void elPanelNuevoSiExigeSesion() throws Exception {
        getConToken(Rutas.PANEL_CATALOGO_PRODUCTOS, null, null).andExpect(status().isUnauthorized());
    }
}
