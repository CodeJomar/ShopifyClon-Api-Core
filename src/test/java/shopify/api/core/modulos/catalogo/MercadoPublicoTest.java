package shopify.api.core.modulos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class MercadoPublicoTest extends PruebaCatalogoBase {

    private Sesion propietario;
    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        propietario = nuevaSesion();
        tienda = tiendaOperable(propietario);
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    private UUID publicado(String titulo, String sku, Object precio, Integer cantidad) throws Exception {
        UUID id = idProducto(crearProducto(propietario, tienda, producto(titulo, variante(sku, precio, cantidad))));
        agregarImagen(propietario, tienda, id);
        postJson(Rutas.panelProductoAccion(id, "publicar"), Map.of(), propietario.token(), tienda).andExpect(status().isOk());
        return id;
    }

    private void fijarVisible(boolean visible) {
        jdbc.update("UPDATE \"Tiendas\" SET \"VisibleEnMercado\" = ? WHERE \"Id\" = ?", visible, tienda);
    }

    private JsonNode productos(String query) throws Exception {
        return cuerpo(getConToken("/mercado/productos" + (query.isEmpty() ? "?" : query + "&") + "tienda=" + subdominio, null, null).andExpect(status().isOk())).get("datos");
    }

    private List<String> titulos(String query) throws Exception {
        List<String> lista = new ArrayList<>();
        productos(query).get("datos").forEach(p -> lista.add(p.get("titulo").asText()));
        return lista;
    }

    @Test
    void listaSoloPublicadosDeTiendasVisiblesYNoExponeCostos() throws Exception {
        publicado("Mochila", "MOC-1", 120, 4);
        crearProducto(propietario, tienda, producto("Borrador", variante("BOR-1", 10, 1)));

        JsonNode pagina = productos("");
        assertThat(pagina.get("total").asInt()).isEqualTo(1);
        JsonNode fila = pagina.get("datos").get(0);
        assertThat(fila.get("titulo").asText()).isEqualTo("Mochila");
        assertThat(fila.get("tienda").get("subdominio").asText()).isEqualTo(subdominio);
        assertThat(fila.get("hay_stock").asBoolean()).isTrue();
        assertThat(fila.toString()).doesNotContain("costo");
    }

    @Test
    void tiendaOcultaOInactivaNoApareceEnNingunaVista() throws Exception {
        publicado("Mochila", "MOC-1", 120, 4);
        fijarVisible(false);

        assertThat(titulos("")).isEmpty();
        assertThat(cuerpo(getConToken("/mercado/tiendas?q=" + subdominio, null, null).andExpect(status().isOk()))
                .get("datos").get("total").asInt()).isZero();
        getConToken("/mercado/tiendas/" + subdominio, null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));

        fijarVisible(true);
        assertThat(titulos("")).hasSize(1);

        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Pausa"), propietario.token(), tienda)
                .andExpect(status().isOk());
        assertThat(titulos("")).isEmpty();
    }

    @Test
    void buscaSinImportarTildesNiMayusculasYEscapaComodines() throws Exception {
        publicado("Camiseta Algodón", "CAM-1", 30, 3);
        publicado("Pantalón", "PAN-1", 50, 3);

        assertThat(titulos("?q=algodon")).containsExactly("Camiseta Algodón");
        assertThat(titulos("?q=PANTALON")).containsExactly("Pantalón");
        assertThat(titulos("?q=%25")).isEmpty();
    }

    @Test
    void ordenaFiltraPorPrecioYPagina() throws Exception {
        publicado("Barato", "B-1", 10, 1);
        publicado("Medio", "M-1", 20, 1);
        publicado("Caro", "C-1", 30, 1);

        assertThat(titulos("?orden=precio_asc")).containsExactly("Barato", "Medio", "Caro");
        assertThat(titulos("?orden=precio_desc")).containsExactly("Caro", "Medio", "Barato");
        assertThat(titulos("?precio_min=15&precio_max=25")).containsExactly("Medio");

        JsonNode pagina = productos("?orden=precio_asc&tamano=2&pagina=1");
        assertThat(pagina.get("total").asInt()).isEqualTo(3);
        assertThat(pagina.get("datos")).hasSize(1);
        assertThat(pagina.get("datos").get(0).get("titulo").asText()).isEqualTo("Caro");
    }

    @Test
    void parametrosInvalidosSon400() throws Exception {
        getConToken("/mercado/productos?orden=inventado", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VAL-003"));
        getConToken("/mercado/productos?tamano=500", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VAL-003"));
        getConToken("/mercado/productos?pagina=-1", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VAL-003"));
        getConToken("/mercado/productos?precio_min=50&precio_max=10", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VAL-003"));
    }

    @Test
    void fichaDeTiendaYDestacados() throws Exception {
        publicado("Mochila", "MOC-1", 120, 4);
        jdbc.update("UPDATE \"Tiendas\" SET \"Descripcion\" = 'Ropa y mochilas' WHERE \"Id\" = ?", tienda);

        JsonNode ficha = cuerpo(getConToken("/mercado/tiendas/" + subdominio, null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(ficha.get("descripcion").asText()).isEqualTo("Ropa y mochilas");
        assertThat(ficha.get("cantidad_productos").asInt()).isEqualTo(1);

        JsonNode destacados = cuerpo(getConToken("/mercado/destacados", null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(destacados.get("tiendas").toString()).contains(subdominio);
        assertThat(destacados.get("productos")).isNotEmpty();
        assertThat(destacados.get("resumen").get("productos").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(destacados.get("resumen").get("tiendas").asInt()).isGreaterThanOrEqualTo(1);
    }
}
