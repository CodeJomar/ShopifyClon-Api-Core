package shopify.api.core.modulos.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Usuarios con token, tiendas operables y cuerpos JSON de producto para las pruebas del catálogo. */
abstract class PruebaCatalogoBase extends PruebaIntegracionBase {

    protected record Sesion(UUID id, String correo, String token) {
    }

    protected Sesion nuevaSesion() {
        UUID id = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
        return new Sesion(id, correo, tokenPara(id, correo));
    }

    /** Tienda con prueba vigente (BASICO: 100 productos) para que el panel de catálogo pueda escribir. */
    protected UUID tiendaOperable(Sesion propietario) {
        return crearTiendaOperativa(propietario.id());
    }

    protected Map<String, Object> variante(String sku, Object precio, Integer cantidadInicial) {
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("sku", sku);
        variante.put("precio", precio);
        if (cantidadInicial != null) {
            variante.put("cantidad_inicial", cantidadInicial);
        }
        return variante;
    }

    protected Map<String, Object> producto(String titulo, Map<String, Object>... variantes) {
        Map<String, Object> producto = new LinkedHashMap<>();
        producto.put("titulo", titulo);
        List<Map<String, Object>> lista = new ArrayList<>(List.of(variantes));
        producto.put("variantes", lista);
        return producto;
    }

    protected JsonNode crearProducto(Sesion sesion, UUID tiendaId, Map<String, Object> cuerpo) throws Exception {
        return cuerpo(postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, cuerpo, sesion.token(), tiendaId)
                .andExpect(status().isCreated())).get("datos");
    }

    protected UUID idProducto(JsonNode datos) {
        return UUID.fromString(datos.get("id").asText());
    }

    /** Añade una imagen https; la primera del producto queda como principal (CAT-010). */
    protected void agregarImagen(Sesion sesion, UUID tiendaId, UUID productoId) throws Exception {
        Map<String, Object> imagen = new LinkedHashMap<>();
        imagen.put("url_imagen", "https://cdn.prueba.test/" + sufijo() + ".jpg");
        postJson(Rutas.panelProductoAccion(productoId, "imagenes"), imagen, sesion.token(), tiendaId)
                .andExpect(status().isCreated());
    }

    protected int contarFilas(String sql, Object... parametros) {
        return jdbc.queryForObject(sql, Integer.class, parametros);
    }
}
