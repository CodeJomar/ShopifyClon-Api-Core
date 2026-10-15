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

class CatalogoPanelTest extends PruebaCatalogoBase {

    private Sesion propietario;
    private UUID tienda;

    @BeforeEach
    void preparar() {
        propietario = nuevaSesion();
        tienda = tiendaOperable(propietario);
    }

    private UUID crearCategoria(UUID tiendaId, String nombre) throws Exception {
        JsonNode datos = cuerpo(postJson(Rutas.PANEL_CATALOGO_CATEGORIAS, Map.of("nombre", nombre),
                propietario.token(), tiendaId).andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("id").asText());
    }

    @Test
    void crearProductoConDosVariantesEInventarioInicialNaceNoPublicado() throws Exception {
        JsonNode datos = crearProducto(propietario, tienda,
                producto("Camiseta", variante("cam-m", 50, 5), variante("cam-l", 50, 2)));
        UUID id = idProducto(datos);

        assertThat(datos.get("esta_publicado").asBoolean()).isFalse();
        assertThat(datos.get("variantes")).hasSize(2);
        assertThat(datos.get("variantes").get(0).get("sku").asText()).isEqualTo("CAM-M");
        assertThat(contarFilas("SELECT count(*) FROM \"VariantesProducto\" WHERE \"ProductoId\" = ?", id)).isEqualTo(2);
        assertThat(contarFilas("SELECT count(*) FROM \"InventarioVariante\" i JOIN \"VariantesProducto\" v "
                + "ON v.\"Id\" = i.\"VarianteId\" WHERE v.\"ProductoId\" = ?", id)).isEqualTo(2);
        assertThat(contarFilas("SELECT count(*) FROM \"MovimientosInventario\" m JOIN \"VariantesProducto\" v "
                + "ON v.\"Id\" = m.\"VarianteId\" WHERE v.\"ProductoId\" = ? AND m.\"Tipo\" = 'INICIAL'", id)).isEqualTo(2);
    }

    @Test
    void mismoSkuEnDosTiendasEsValidoYEnLaMismaEs422() throws Exception {
        crearProducto(propietario, tienda, producto("Gorra", variante("GOR-1", 10, 0)));
        UUID otra = tiendaOperable(propietario);
        crearProducto(propietario, otra, producto("Gorra", variante("GOR-1", 10, 0)));

        postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, producto("Otra gorra", variante(" gor-1 ", 10, 0)),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-003"));
    }

    @Test
    void slugRepetidoAnadeSufijoNumerico() throws Exception {
        UUID primero = idProducto(crearProducto(propietario, tienda, producto("Gorra", variante("A-1", 10, 0))));
        UUID segundo = idProducto(crearProducto(propietario, tienda, producto("Gorra", variante("A-2", 10, 0))));

        assertThat(jdbc.queryForObject("SELECT \"IdentificadorUrl\" FROM \"Productos\" WHERE \"Id\" = ?", String.class, primero))
                .isEqualTo("gorra");
        assertThat(jdbc.queryForObject("SELECT \"IdentificadorUrl\" FROM \"Productos\" WHERE \"Id\" = ?", String.class, segundo))
                .isEqualTo("gorra-2");
    }

    @Test
    void publicarSinImagenPrincipalEs422CAT007Y_conImagenSePublica() throws Exception {
        UUID id = idProducto(crearProducto(propietario, tienda, producto("Bolso", variante("BOL-1", 80, 3))));

        postJson(Rutas.panelProductoAccion(id, "publicar"), Map.of(), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-007"));

        agregarImagen(propietario, tienda, id);
        JsonNode publicado = cuerpo(postJson(Rutas.panelProductoAccion(id, "publicar"), Map.of(),
                propietario.token(), tienda).andExpect(status().isOk())).get("datos");
        assertThat(publicado.get("esta_publicado").asBoolean()).isTrue();
    }

    @Test
    void noSeBorraLaUltimaVariante() throws Exception {
        JsonNode datos = crearProducto(propietario, tienda, producto("Taza", variante("TAZA-1", 15, 4)));
        UUID id = idProducto(datos);
        UUID unica = UUID.fromString(datos.get("variantes").get(0).get("id").asText());

        delete(Rutas.PANEL_CATALOGO_PRODUCTOS.replace("productos", "variantes") + "/" + unica,
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-001"));

        postJson(Rutas.panelProductoAccion(id, "variantes"), variante("TAZA-2", 15, 0),
                propietario.token(), tienda).andExpect(status().isCreated());
        delete(Rutas.PANEL_CATALOGO_PRODUCTOS.replace("productos", "variantes") + "/" + unica,
                propietario.token(), tienda).andExpect(status().isOk());
    }

    @Test
    void productoDigitalFuerzaSinEnvioEnSusVariantes() throws Exception {
        Map<String, Object> pedido = producto("Ebook", variante("EBK-1", 9, 0));
        pedido.put("es_digital", true);
        ((Map<String, Object>) ((List<?>) pedido.get("variantes")).get(0)).put("requiere_envio", true);

        JsonNode datos = crearProducto(propietario, tienda, pedido);

        assertThat(datos.get("es_digital").asBoolean()).isTrue();
        assertThat(datos.get("variantes").get(0).get("requiere_envio").asBoolean()).isFalse();
    }

    @Test
    void precioNegativoEs422VAL001() throws Exception {
        postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, producto("Mal precio", variante("NEG-1", -1, 0)),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
    }

    @Test
    void elPlanBasicoDeCienProductosDeja422CAT006AlCiento1() throws Exception {
        for (int i = 0; i < 100; i++) {
            jdbc.update("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\") VALUES (?, 'Relleno', ?)",
                    tienda, "relleno-" + sufijo());
        }

        postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, producto("Número 101", variante("N-101", 10, 0)),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-006"));
    }

    @Test
    void gestorDePedidosRecibe403EnElCatalogo() throws Exception {
        Sesion gestor = nuevaSesion();
        agregarPersonal(tienda, gestor.id(), "GESTOR_PEDIDOS");

        getConToken(Rutas.PANEL_CATALOGO_PRODUCTOS, gestor.token(), tienda).andExpect(status().isForbidden());
    }

    @Test
    void productoDeOtraTiendaEs404() throws Exception {
        UUID id = idProducto(crearProducto(propietario, tienda, producto("Reloj", variante("REL-1", 99, 1))));
        UUID otra = tiendaOperable(propietario);

        getConToken(Rutas.panelProducto(id), propietario.token(), otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAT-404"));
    }

    @Test
    void categoriaDeOtraTiendaEs404AlAsignarla() throws Exception {
        UUID otra = tiendaOperable(propietario);
        UUID ajena = crearCategoria(otra, "Ajena");

        postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, Map.of("titulo", "Propio", "categoria_ids", List.of(ajena),
                        "variantes", List.of(variante("PRO-1", 5, 0))), propietario.token(), tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAT-404"));
    }

    @Test
    void categoriaTieneSlugYAlEliminarseQuitaSusAsignaciones() throws Exception {
        UUID categoria = crearCategoria(tienda, "Ropa de verano");
        assertThat(jdbc.queryForObject("SELECT \"IdentificadorUrl\" FROM \"Categorias\" WHERE \"Id\" = ?", String.class, categoria))
                .isEqualTo("ropa-de-verano");

        UUID producto = idProducto(cuerpo(postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, Map.of("titulo", "Vestido",
                "categoria_ids", List.of(categoria), "variantes", List.of(variante("VES-1", 40, 1))),
                propietario.token(), tienda).andExpect(status().isCreated())).get("datos"));

        assertThat(cuerpo(getConToken(Rutas.panelProducto(producto), propietario.token(), tienda)).get("datos")
                .get("categorias")).hasSize(1);

        delete(Rutas.panelCategoria(categoria), propietario.token(), tienda).andExpect(status().isOk());

        assertThat(cuerpo(getConToken(Rutas.panelProducto(producto), propietario.token(), tienda)).get("datos")
                .get("categorias")).isEmpty();
    }

    @Test
    void slugInvalidoAlEditarUnaCategoriaEs422CAT002() throws Exception {
        UUID categoria = crearCategoria(tienda, "Calzado");

        putJson(Rutas.panelCategoria(categoria), Map.of("nombre", "Calzado", "identificador_url", "Mal Slug!"),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-002"));
        putJson(Rutas.panelCategoria(categoria), Map.of("nombre", "Calzado", "identificador_url", "-calzado-"),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-002"));

        assertThat(slugDe(categoria)).isEqualTo("calzado");
    }

    @Test
    void slugRepetidoAlEditarUnaCategoriaEs422CAT002() throws Exception {
        crearCategoria(tienda, "Calzado");
        UUID accesorios = crearCategoria(tienda, "Accesorios");

        putJson(Rutas.panelCategoria(accesorios), Map.of("nombre", "Accesorios", "identificador_url", "calzado"),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAT-002"));
        assertThat(slugDe(accesorios)).isEqualTo("accesorios");

        // Conservar su propio slug no es un conflicto: la comprobación excluye la categoría editada
        putJson(Rutas.panelCategoria(accesorios), Map.of("nombre", "Accesorios", "identificador_url", "accesorios"),
                propietario.token(), tienda)
                .andExpect(status().isOk());
    }

    private String slugDe(UUID categoria) {
        return jdbc.queryForObject("SELECT \"IdentificadorUrl\" FROM \"Categorias\" WHERE \"Id\" = ?", String.class, categoria);
    }
}
