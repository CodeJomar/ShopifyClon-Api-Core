package shopify.api.core.concurrencia;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Preparación común de las pruebas de concurrencia: tienda, variantes con stock, compras por HTTP y pagos manuales. */
public abstract class PruebaConcurrenciaBase extends PruebaIntegracionBase {

    protected static final String CARRITO = "X-Carrito-Token";
    protected static final String CLAVE = "Idempotency-Key";

    protected UUID tienda;
    protected String subdominio;

    protected void prepararTienda() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    /** Variante publicada con inventario que rastrea. */
    protected UUID variante(BigDecimal precio, int stock, boolean requiereEnvio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, ?) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio, requiereEnvio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, TRUE, FALSE)", variante, stock);
        return variante;
    }

    /** Compra anónima por HTTP (carrito + checkout). Devuelve el id y el token de acceso de la orden. */
    protected Compra comprar(UUID variante, int cantidad, String pasarela, BigDecimal totalEsperado) throws Exception {
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", pasarela);
        pedido.put("total_esperado", totalEsperado);
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return new Compra(UUID.fromString(datos.get("orden_id").asText()), datos.get("token_acceso").asText());
    }

    protected record Compra(UUID orden, String token) {
    }

    /** Pago manual de una orden por un usuario con el rol dado. */
    protected void pagarManual(UUID orden, String jwt) throws Exception {
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    protected String personal(UUID tiendaId, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    protected int disponible(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    protected int reservada(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    protected int movimientos(UUID variante, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, variante, tipo);
    }

    protected static int estado(ResultActions resultado) throws Exception {
        return resultado.andReturn().getResponse().getStatus();
    }

    protected static String codigo(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andReturn().getResponse().getContentAsString();
        return cuerpo.contains("\"codigo\"") ? cuerpo.replaceAll(".*\"codigo\":\"([^\"]+)\".*", "$1") : "";
    }
}
