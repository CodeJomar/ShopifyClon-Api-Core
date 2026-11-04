package shopify.api.core.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Base para pruebas de envíos, eventos y rutas: una tienda operativa con un gestor de pedidos y utilidades para crear
 * órdenes físicas pagadas y sus envíos por la API (checkout, pago manual y preparación).
 */
public abstract class PruebaConEnvios extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    protected UUID tienda;
    protected String subdominio;
    protected String gestorJwt;

    @BeforeEach
    void prepararTiendaConEnvios() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        gestorJwt = jwtDePersonal(tienda, "GESTOR_PEDIDOS");
    }

    /** Token de un usuario nuevo agregado al personal de la tienda con ese rol. */
    protected String jwtDePersonal(UUID tiendaId, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    /** Orden física de la tienda (100.00 + tarifa 5.00), pagada con el pago manual, con su envío creado (CREADO). */
    protected UUID envioCreado() throws Exception {
        UUID orden = ordenFisicaPagada();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("empresa_transporte", "Coordinadora");
        return UUID.fromString(cuerpo(postJson(Rutas.PANEL_ORDENES + "/" + orden + "/envio", cuerpo, gestorJwt, tienda)
                .andExpect(status().isCreated())).get("datos").get("id").asText());
    }

    protected UUID ordenFisicaPagada() throws Exception {
        UUID orden = ordenFisica();
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
        return orden;
    }

    protected UUID ordenFisica() throws Exception {
        UUID variante = variante(new BigDecimal("100.00"));
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        UUID tarifa = jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', 5.00, TRUE) RETURNING \"Id\"", UUID.class, tienda);
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombre_contacto", "Ana Perez");
        direccion.put("telefono", "999888777");
        direccion.put("direccion_linea1", "Av. Siempre Viva 123");
        direccion.put("ciudad", "Lima");
        direccion.put("estado_provincia", "Lima");
        direccion.put("codigo_postal", "15001");
        direccion.put("pais", "PE");
        direccion.put("latitud", -12.0464);
        direccion.put("longitud", -77.0428);
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("direccion_envio", direccion);
        pedido.put("tarifa_envio_id", tarifa);
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", new BigDecimal("105.00"));
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    private UUID variante(BigDecimal precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, TRUE) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 10, 0, TRUE, FALSE)", variante);
        return variante;
    }
}
