package shopify.api.core.modulos.pago;

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
import shopify.api.core.modulos.pago.pasarela.PasarelaEnLineaFalsa;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B6: un solo cobro por el pedido, webhook que confirma todas las órdenes y libro de repartos. */
class PagoPedidoTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String WEBHOOK = "/webhooks/stripe";

    private UUID tiendaA;
    private UUID tiendaB;
    private UUID varA;
    private UUID varB;
    private JsonNode pedido;
    private String tokenPedido;
    private BigDecimal total;

    @BeforeEach
    void preparar() throws Exception {
        tiendaA = crearTiendaOperativa(crearUsuarioActivo(false));
        tiendaB = crearTiendaOperativa(crearUsuarioActivo(false));
        varA = variante(tiendaA, "10.00");
        varB = variante(tiendaB, "25.00");
        pedido = crearPedido("STRIPE");
        tokenPedido = pedido.get("token_acceso").asText();
        total = pedido.get("total").decimalValue();
    }

    private UUID variante(UUID tienda, String precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, FALSE) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), new BigDecimal(precio));
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
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

    /** Pedido de dos tiendas con productos digitales (sin envío), pagable con la pasarela indicada. */
    private JsonNode crearPedido(String pasarela) throws Exception {
        String token = cuerpo(enviar(post("/carrito/items"), null, null, Map.of("variante_id", varA, "cantidad", 2))
                .andExpect(status().isOk())).get("datos").get("token").asText();
        enviar(post("/carrito/items"), token, null, Map.of("variante_id", varB, "cantidad", 1)).andExpect(status().isOk());
        BigDecimal esperado = cuerpo(enviar(post("/carrito/cotizar"), token, null, Map.of("envios", List.of()))
                .andExpect(status().isOk())).get("datos").get("total_general").decimalValue();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo_cliente", "ana-" + sufijo() + "@prueba.test");
        cuerpo.put("nombre_cliente", "Ana Pérez");
        cuerpo.put("pasarela", pasarela);
        cuerpo.put("total_esperado", esperado);
        return cuerpo(enviar(post("/checkout"), token, UUID.randomUUID().toString(), cuerpo)
                .andExpect(status().isCreated())).get("datos");
    }

    private ResultActions pagarPedido() throws Exception {
        return mvc.perform(post("/pedidos/" + tokenPedido + "/pagar"));
    }

    private ResultActions webhook(String intento, long centavos) throws Exception {
        String cuerpo = json.writeValueAsString(Map.of("type", "payment_intent.succeeded",
                "data", Map.of("object", Map.of("id", intento, "amount", centavos))));
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("Stripe-Signature", PasarelaEnLineaFalsa.firmar(cuerpo)));
    }

    private String intentoDelPedido() {
        List<String> intentos = jdbc.queryForList("SELECT DISTINCT p.\"TransaccionId\" FROM \"Pagos\" p "
                + "JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" WHERE o.\"PedidoId\" = ?::uuid",
                String.class, pedido.get("pedido_id").asText());
        assertThat(intentos).hasSize(1);
        return intentos.get(0);
    }

    private long centavos() {
        return total.movePointRight(2).longValueExact();
    }

    @Test
    void unSoloCobroPorElTotalConUnPagoPorOrden() throws Exception {
        JsonNode datos = cuerpo(pagarPedido().andExpect(status().isOk())).get("datos");

        assertThat(datos.get("client_secret").asText()).startsWith("pi_test_secret_");
        assertThat(datos.get("monto").decimalValue()).isEqualByComparingTo(total);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" p JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" "
                + "WHERE o.\"PedidoId\" = ?::uuid AND p.\"Estado\" = 'PENDIENTE'", Integer.class,
                pedido.get("pedido_id").asText())).isEqualTo(2);
        intentoDelPedido();

        // Repetir devuelve el mismo intento sin crear más pagos
        String segundo = cuerpo(pagarPedido().andExpect(status().isOk())).get("datos").get("client_secret").asText();
        assertThat(segundo).isEqualTo(datos.get("client_secret").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" p JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" "
                + "WHERE o.\"PedidoId\" = ?::uuid", Integer.class, pedido.get("pedido_id").asText())).isEqualTo(2);
    }

    @Test
    void elWebhookPagaTodasLasOrdenesRepartePorTiendaYEsIdempotente() throws Exception {
        pagarPedido().andExpect(status().isOk());
        String intento = intentoDelPedido();

        webhook(intento, centavos()).andExpect(status().isOk());

        String id = pedido.get("pedido_id").asText();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"PedidoId\" = ?::uuid AND \"EstadoPago\" = 'PAGADO'",
                Integer.class, id)).isEqualTo(2);
        List<Map<String, Object>> repartos = jdbc.queryForList(
                "SELECT \"TiendaId\", \"Bruto\", \"Comision\", \"Neto\" FROM \"RepartosPago\" WHERE \"PedidoId\" = ?::uuid", id);
        assertThat(repartos).hasSize(2);
        BigDecimal bruto = BigDecimal.ZERO;
        for (Map<String, Object> fila : repartos) {
            BigDecimal b = (BigDecimal) fila.get("Bruto");
            assertThat(((BigDecimal) fila.get("Neto"))).isEqualByComparingTo(b.subtract((BigDecimal) fila.get("Comision")));
            bruto = bruto.add(b);
        }
        assertThat(bruto).isEqualByComparingTo(total);
        // El balance de cada tienda recibió su parte
        assertThat(jdbc.queryForObject("SELECT \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", Integer.class,
                tiendaA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", Integer.class,
                tiendaB)).isEqualTo(1);

        // El mismo evento dos veces no repite nada
        webhook(intento, centavos()).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RepartosPago\" WHERE \"PedidoId\" = ?::uuid", Integer.class, id))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", Integer.class,
                tiendaA)).isEqualTo(1);

        // El pedido figura pagado y entregado (productos digitales sin envío)
        JsonNode leido = cuerpo(mvc.perform(get("/pedidos/" + tokenPedido)).andExpect(status().isOk())).get("datos");
        assertThat(leido.get("estado_pago").asText()).isEqualTo("PAGADO");
        assertThat(leido.get("estado").asText()).isEqualTo("ENTREGADO");
    }

    @Test
    void unMontoQueNoCoincideRechazaTodosLosPagosYNoPagaNada() throws Exception {
        pagarPedido().andExpect(status().isOk());
        String intento = intentoDelPedido();

        webhook(intento, centavos() - 100).andExpect(status().isOk());

        String id = pedido.get("pedido_id").asText();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"PedidoId\" = ?::uuid AND \"EstadoPago\" = 'PAGADO'",
                Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" p JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" "
                + "WHERE o.\"PedidoId\" = ?::uuid AND p.\"Estado\" = 'RECHAZADO'", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RepartosPago\" WHERE \"PedidoId\" = ?::uuid", Integer.class, id))
                .isZero();
    }

    @Test
    void unaOrdenHijaNoSePagaSueltaYUnPedidoManualNoSePagaEnLinea() throws Exception {
        String subdominioA = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tiendaA);
        String tokenOrdenA = null;
        for (JsonNode orden : pedido.get("ordenes")) {
            if (orden.get("tienda").get("subdominio").asText().equals(subdominioA)) {
                tokenOrdenA = orden.get("token_acceso").asText();
            }
        }
        mvc.perform(post("/vitrina/" + subdominioA + "/ordenes/" + tokenOrdenA + "/pagar"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAG-002"));

        // Otro pedido con pasarela MANUAL
        varA = variante(tiendaA, "10.00");
        varB = variante(tiendaB, "25.00");
        JsonNode manual = crearPedido("MANUAL");
        mvc.perform(post("/pedidos/" + manual.get("token_acceso").asText() + "/pagar"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("PAG-002"));
        mvc.perform(post("/pedidos/token-que-no-existe/pagar")).andExpect(status().isNotFound());
    }

    @Test
    void cadaTiendaVeSusMovimientosYLaPlataformaTodos() throws Exception {
        pagarPedido().andExpect(status().isOk());
        webhook(intentoDelPedido(), centavos()).andExpect(status().isOk());

        UUID duenoA = jdbc.queryForObject("SELECT \"PropietarioId\" FROM \"Tiendas\" WHERE \"Id\" = ?", UUID.class, tiendaA);
        String correoA = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, duenoA);
        JsonNode movimientos = cuerpo(getConToken("/panel/balance/movimientos", tokenPara(duenoA, correoA), tiendaA)
                .andExpect(status().isOk())).get("datos");
        assertThat(movimientos.get("total").asInt()).isEqualTo(1);
        assertThat(movimientos.get("datos").get(0).get("tienda_id").asText()).isEqualTo(tiendaA.toString());

        UUID admin = crearUsuarioActivo(true);
        String correoAdmin = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, admin);
        JsonNode todos = cuerpo(getConToken("/plataforma/repartos?pedido_id=" + pedido.get("pedido_id").asText(),
                tokenPara(admin, correoAdmin), null).andExpect(status().isOk())).get("datos");
        assertThat(todos.get("total").asInt()).isEqualTo(2);
        getConToken("/plataforma/repartos", tokenPara(duenoA, correoA), null).andExpect(status().isNotFound());
    }

    @Test
    void elReembolsoDeUnaOrdenHijaDejaAsientoNegativoYNoTocaLaOtra() throws Exception {
        pagarPedido().andExpect(status().isOk());
        webhook(intentoDelPedido(), centavos()).andExpect(status().isOk());
        String subdominioA = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tiendaA);
        String ordenA = null;
        String ordenB = null;
        for (JsonNode orden : pedido.get("ordenes")) {
            if (orden.get("tienda").get("subdominio").asText().equals(subdominioA)) {
                ordenA = orden.get("orden_id").asText();
            } else {
                ordenB = orden.get("orden_id").asText();
            }
        }
        UUID duenoA = jdbc.queryForObject("SELECT \"PropietarioId\" FROM \"Tiendas\" WHERE \"Id\" = ?", UUID.class, tiendaA);
        String correoA = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, duenoA);
        String jwtA = tokenPara(duenoA, correoA);

        postJson("/panel/ordenes/" + ordenA + "/reembolsos", Map.of("monto", 5.00, "motivo", "Producto dañado"), jwtA, tiendaA)
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?::uuid", String.class, ordenA))
                .isEqualTo("PARCIALMENTE_REEMBOLSADO");
        assertThat(jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?::uuid", String.class, ordenB))
                .isEqualTo("PAGADO");
        List<Map<String, Object>> asientos = jdbc.queryForList(
                "SELECT \"Bruto\", \"Neto\", \"TiendaId\" FROM \"RepartosPago\" WHERE \"Tipo\" = 'REEMBOLSO' AND \"PedidoId\" = ?::uuid",
                pedido.get("pedido_id").asText());
        assertThat(asientos).hasSize(1);
        assertThat((BigDecimal) asientos.get(0).get("Bruto")).isEqualByComparingTo("-5.00");
        assertThat((BigDecimal) asientos.get(0).get("Neto")).isEqualByComparingTo("-5.00");
        assertThat(asientos.get(0).get("TiendaId")).isEqualTo(tiendaA);

        // El panel de la tienda ve a qué pedido pertenece cada orden y nada de la otra tienda
        JsonNode detalle = cuerpo(getConToken("/panel/ordenes/" + ordenA, jwtA, tiendaA).andExpect(status().isOk())).get("datos");
        assertThat(detalle.get("pedido_id").asText()).isEqualTo(pedido.get("pedido_id").asText());
        assertThat(detalle.get("numero_pedido").asText()).isEqualTo(pedido.get("numero_pedido").asText());
        JsonNode lista = cuerpo(getConToken("/panel/ordenes?estado_pago=PARCIALMENTE_REEMBOLSADO", jwtA, tiendaA)
                .andExpect(status().isOk())).get("datos");
        assertThat(lista.get("datos").get(0).get("numero_pedido").asText()).isEqualTo(pedido.get("numero_pedido").asText());
        getConToken("/panel/ordenes/" + ordenB, jwtA, tiendaA).andExpect(status().isNotFound());
    }
}
