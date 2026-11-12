package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B5: checkout global, pedido padre con órdenes hijas y su lectura. */
class CheckoutGlobalTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";

    private UUID tiendaA;
    private UUID tiendaB;
    private UUID tarifaA;
    private UUID tarifaB;
    private UUID varA;
    private UUID varB;
    private String token;

    @BeforeEach
    void preparar() throws Exception {
        tiendaA = crearTiendaOperativa(crearUsuarioActivo(false));
        tiendaB = crearTiendaOperativa(crearUsuarioActivo(false));
        tarifaA = tarifa(tiendaA, "4.00");
        tarifaB = tarifa(tiendaB, "6.00");
        varA = variante(tiendaA, "10.00", 50);
        varB = variante(tiendaB, "25.00", 50);
        token = agregar(null, varA, 2);
        agregar(token, varB, 1);
    }

    private UUID variante(UUID tienda, String precio, int stock) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, TRUE) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), new BigDecimal(precio));
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, TRUE, FALSE)", variante, stock);
        return variante;
    }

    private UUID tarifa(UUID tienda, String precio) {
        return jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, new BigDecimal(precio));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder builder, String carrito, String clave, Object cuerpo) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (carrito != null) {
            builder.header(CARRITO, carrito);
        }
        if (clave != null) {
            builder.header("Idempotency-Key", clave);
        }
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(builder);
    }

    private String agregar(String carrito, UUID variante, int cantidad) throws Exception {
        return cuerpo(enviar(post("/carrito/items"), carrito, null, Map.of("variante_id", variante, "cantidad", cantidad))
                .andExpect(status().isOk())).get("datos").get("token").asText();
    }

    private List<Map<String, Object>> envios() {
        return List.of(Map.of("tienda_id", tiendaA, "tarifa_envio_id", tarifaA),
                Map.of("tienda_id", tiendaB, "tarifa_envio_id", tarifaB));
    }

    private BigDecimal totalCotizado() throws Exception {
        return cuerpo(enviar(post("/carrito/cotizar"), token, null, Map.of("envios", envios())).andExpect(status().isOk()))
                .get("datos").get("total_general").decimalValue();
    }

    private Map<String, Object> cuerpoCheckout(BigDecimal total) {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombre_contacto", "Ana Pérez");
        direccion.put("direccion_linea1", "Av. Siempre Viva 123");
        direccion.put("ciudad", "Lima");
        direccion.put("estado_provincia", "Lima");
        direccion.put("codigo_postal", "15001");
        direccion.put("pais", "PE");
        direccion.put("latitud", -12.04);
        direccion.put("longitud", -77.03);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo_cliente", "ana-" + sufijo() + "@prueba.test");
        cuerpo.put("nombre_cliente", "Ana Pérez");
        cuerpo.put("direccion_envio", direccion);
        cuerpo.put("envios", envios());
        cuerpo.put("pasarela", "MANUAL");
        cuerpo.put("total_esperado", total);
        return cuerpo;
    }

    @Test
    void creaUnPedidoConUnaOrdenHijaPorTiendaYEsIdempotente() throws Exception {
        BigDecimal total = totalCotizado();
        String clave = UUID.randomUUID().toString();
        Map<String, Object> cuerpo = cuerpoCheckout(total);

        JsonNode pedido = cuerpo(enviar(post("/checkout"), token, clave, cuerpo).andExpect(status().isCreated())).get("datos");

        assertThat(pedido.get("numero_pedido").asText()).startsWith("P-");
        assertThat(pedido.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(pedido.get("total").decimalValue()).isEqualByComparingTo(total);
        assertThat(pedido.get("ordenes")).hasSize(2);
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode orden : pedido.get("ordenes")) {
            suma = suma.add(orden.get("total").decimalValue());
            assertThat(orden.get("numero_orden").asText()).startsWith("#");
            assertThat(orden.get("token_acceso").asText()).isNotBlank();
            assertThat(orden.get("tienda").get("subdominio").asText()).isNotBlank();
        }
        assertThat(suma).isEqualByComparingTo(total);

        // Repetir con la misma clave devuelve el mismo pedido (200) y no duplica nada
        JsonNode repetido = cuerpo(enviar(post("/checkout"), token, clave, cuerpo).andExpect(status().isOk())).get("datos");
        assertThat(repetido.get("pedido_id").asText()).isEqualTo(pedido.get("pedido_id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"PedidoId\" = ?::uuid", Integer.class,
                pedido.get("pedido_id").asText())).isEqualTo(2);

        // El carrito quedó convertido y vacío; el stock se reservó por tienda
        assertThat(cuerpo(enviar(get("/carrito"), token, null, null).andExpect(status().isOk())).get("datos").get("tiendas")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, varA)).isEqualTo(2);

        // Vista pública por token del pedido
        JsonNode publico = cuerpo(mvc.perform(get("/pedidos/" + pedido.get("token_acceso").asText()))
                .andExpect(status().isOk())).get("datos");
        assertThat(publico.get("numero_pedido").asText()).isEqualTo(pedido.get("numero_pedido").asText());
        mvc.perform(get("/pedidos/token-inexistente")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PED-404"));
    }

    @Test
    void unTotalDistintoDaChk004YNoDejaNadaCreado() throws Exception {
        BigDecimal total = totalCotizado();
        Map<String, Object> cuerpo = cuerpoCheckout(total.add(BigDecimal.ONE));

        enviar(post("/checkout"), token, UUID.randomUUID().toString(), cuerpo)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CHK-004"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"CorreoCliente\" = ?", Integer.class,
                cuerpo.get("correo_cliente"))).isZero();
        assertThat(jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, varA)).isZero();
        assertThat(cuerpo(enviar(get("/carrito"), token, null, null).andExpect(status().isOk())).get("datos").get("tiendas")).hasSize(2);
    }

    @Test
    void siUnaTiendaNoTieneStockFallaTodoElPedido() throws Exception {
        BigDecimal total = totalCotizado();
        jdbc.update("UPDATE \"InventarioVariante\" SET \"CantidadDisponible\" = 0 WHERE \"VarianteId\" = ?", varB);
        Map<String, Object> cuerpo = cuerpoCheckout(total);

        enviar(post("/checkout"), token, UUID.randomUUID().toString(), cuerpo).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"CorreoCliente\" = ?", Integer.class,
                cuerpo.get("correo_cliente"))).isZero();
        assertThat(jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, varA)).isZero();
    }

    @Test
    void faltaLaClaveDeIdempotenciaOElEnvioDeUnaTienda() throws Exception {
        BigDecimal total = totalCotizado();
        enviar(post("/checkout"), token, null, cuerpoCheckout(total)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CHK-007"));

        Map<String, Object> sinEnvioB = cuerpoCheckout(total);
        sinEnvioB.put("envios", List.of(Map.of("tienda_id", tiendaA, "tarifa_envio_id", tarifaA)));
        enviar(post("/checkout"), token, UUID.randomUUID().toString(), sinEnvioB).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-001"));
    }

    @Test
    void elCompradorRegistradoVeSuHistorialYNoElDeOtros() throws Exception {
        UUID usuario = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        String jwt = tokenPara(usuario, correo);
        BigDecimal total = totalCotizado();

        JsonNode pedido = cuerpo(enviar(post("/checkout").header("Authorization", "Bearer " + jwt), token,
                UUID.randomUUID().toString(), cuerpoCheckout(total)).andExpect(status().isCreated())).get("datos");

        JsonNode historial = cuerpo(mvc.perform(get("/cuenta/pedidos").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())).get("datos");
        assertThat(historial.get("total").asInt()).isEqualTo(1);
        String id = pedido.get("pedido_id").asText();
        mvc.perform(get("/cuenta/pedidos/" + id).header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());

        UUID otro = crearUsuarioActivo(false);
        String correoOtro = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, otro);
        mvc.perform(get("/cuenta/pedidos/" + id).header("Authorization", "Bearer " + tokenPara(otro, correoOtro)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PED-404"));
        mvc.perform(get("/cuenta/pedidos")).andExpect(status().isUnauthorized());
    }
}
