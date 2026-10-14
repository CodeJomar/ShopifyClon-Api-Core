package shopify.api.core.modulos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class CatalogoVitrinaTest extends PruebaCatalogoBase {

    private Sesion propietario;
    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        propietario = nuevaSesion();
        tienda = tiendaOperable(propietario);
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    /** Publica un producto: primero una imagen principal (CAT-007). */
    private void publicar(UUID productoId) throws Exception {
        agregarImagen(propietario, tienda, productoId);
        postJson(Rutas.panelProductoAccion(productoId, "publicar"), Map.of(), propietario.token(), tienda)
                .andExpect(status().isOk());
    }

    private UUID productoPublicado(String titulo, String sku, Object precio, Integer cantidad) throws Exception {
        UUID id = idProducto(crearProducto(propietario, tienda, producto(titulo, variante(sku, precio, cantidad))));
        publicar(id);
        return id;
    }

    private JsonNode listar(String query) throws Exception {
        return cuerpo(getConToken(Rutas.vitrina(subdominio) + "/productos" + query, null, null)
                .andExpect(status().isOk())).get("datos").get("datos");
    }

    @Test
    void laVitrinaSoloListaPublicadosYNuncaMuestraCostos() throws Exception {
        UUID publicado = productoPublicado("Mochila", "MOC-1", 120, 4);
        crearProducto(propietario, tienda, producto("Sin publicar", variante("SIN-1", 10, 1)));

        JsonNode lista = listar("");
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("titulo").asText()).isEqualTo("Mochila");

        String respuesta = getConToken(Rutas.vitrina(subdominio) + "/productos/mochila", null, null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain("costo");
        assertThat(respuesta).contains("\"titulo\":\"Mochila\"");
        assertThat(publicado).isNotNull();

        getConToken(Rutas.vitrina(subdominio) + "/productos/sin-publicar", null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAT-404"));
    }

    @Test
    void tiendaNoOperativaResponde404TIE404() throws Exception {
        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Pausa"), propietario.token(), tienda)
                .andExpect(status().isOk());

        getConToken(Rutas.vitrina(subdominio) + "/productos", null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
        getConToken(Rutas.vitrina(subdominio) + "/categorias", null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
    }

    @Test
    void disponibleRespetaRastreoYVentaSinStock() throws Exception {
        UUID agotado = productoPublicado("Agotado", "AGO-1", 10, 0);
        UUID sinStockPermitida = idProducto(crearProducto(propietario, tienda, producto("Bajo pedido",
                Map.of("sku", "BAJ-1", "precio", 10, "cantidad_inicial", 0, "permitir_venta_sin_stock", true))));
        publicar(sinStockPermitida);
        UUID sinRastreo = idProducto(crearProducto(propietario, tienda, producto("Digital",
                Map.of("sku", "DIG-1", "precio", 10, "cantidad_inicial", 0, "rastrea_inventario", false))));
        publicar(sinRastreo);

        assertThat(disponiblePor(listar(""), "Agotado")).isFalse();
        assertThat(disponiblePor(listar(""), "Bajo pedido")).isTrue();
        assertThat(disponiblePor(listar(""), "Digital")).isTrue();
        assertThat(agotado).isNotNull();
    }

    @Test
    void ordenaPorPrecioYFiltraPorCategoria() throws Exception {
        productoPublicado("Barato", "B-1", 10, 1);
        productoPublicado("Medio", "M-1", 20, 1);
        productoPublicado("Caro", "C-1", 30, 1);

        assertThat(listar("?orden=precio_asc").get(0).get("titulo").asText()).isEqualTo("Barato");
        assertThat(listar("?orden=precio_desc").get(0).get("titulo").asText()).isEqualTo("Caro");

        UUID categoria = UUID.fromString(cuerpo(postJson(Rutas.PANEL_CATALOGO_CATEGORIAS, Map.of("nombre", "Ofertas"),
                propietario.token(), tienda).andExpect(status().isCreated())).get("datos").get("id").asText());
        jdbc.update("INSERT INTO \"ProductosCategorias\" (\"ProductoId\", \"CategoriaId\") "
                + "SELECT \"Id\", ? FROM \"Productos\" WHERE \"Titulo\" = 'Medio'", categoria);

        JsonNode filtrada = listar("?categoria=ofertas");
        assertThat(filtrada).hasSize(1);
        assertThat(filtrada.get(0).get("titulo").asText()).isEqualTo("Medio");
    }

    @Test
    void categoriasSoloConProductosPublicados() throws Exception {
        productoPublicado("Zapato", "ZAP-1", 50, 2);
        cuerpo(postJson(Rutas.PANEL_CATALOGO_CATEGORIAS, Map.of("nombre", "Vacía"), propietario.token(), tienda)
                .andExpect(status().isCreated()));

        JsonNode categorias = cuerpo(getConToken(Rutas.vitrina(subdominio) + "/categorias", null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(categorias).isEmpty();
    }

    @Test
    void ordenInvalidoEs400VAL003() throws Exception {
        getConToken(Rutas.vitrina(subdominio) + "/productos?orden=inventado", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VAL-003"));
    }

    private static boolean disponiblePor(JsonNode lista, String titulo) {
        for (JsonNode producto : lista) {
            if (producto.get("titulo").asText().equals(titulo)) {
                return producto.get("disponible").asBoolean();
            }
        }
        throw new AssertionError("No aparece " + titulo + " en la vitrina");
    }
}
