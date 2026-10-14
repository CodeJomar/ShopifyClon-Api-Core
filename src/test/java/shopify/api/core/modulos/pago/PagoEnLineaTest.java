package shopify.api.core.modulos.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.modulos.pago.pasarela.PasarelaEnLineaFalsa;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Pago en línea con Stripe (pasarela falsa): inicio, reutilización, webhook idempotente, firma y PAG-006. */
class PagoEnLineaTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";
    private static final String FIRMA = "Stripe-Signature";
    private static final String WEBHOOK = "/webhooks/stripe";

    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    // ---- iniciar el pago ----

    @Test
    void iniciarCreaUnCargoPendienteYDevuelveElClientSecret() throws Exception {
        JsonNode orden = ordenStripe();

        JsonNode datos = cuerpo(pagar(orden.get("token").asText()).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("client_secret").asText()).startsWith("pi_test_secret_");
        assertThat(datos.get("monto").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(datos.get("moneda").asText()).isEqualTo("USD");
        assertThat(pagos(orden.get("id").asText())).isEqualTo(1);
        assertThat(estadoPago(orden.get("id").asText())).isEqualTo("PENDIENTE");
        assertThat(intentoDe(orden.get("id").asText())).startsWith("pi_test_");
    }

    @Test
    void repetirElPagoDevuelveElMismoIntentoSinCrearOtroPago() throws Exception {
        JsonNode orden = ordenStripe();
        String primero = cuerpo(pagar(orden.get("token").asText()).andExpect(status().isOk())).get("datos")
                .get("client_secret").asText();

        String segundo = cuerpo(pagar(orden.get("token").asText()).andExpect(status().isOk())).get("datos")
                .get("client_secret").asText();

        assertThat(segundo).isEqualTo(primero);
        assertThat(pagos(orden.get("id").asText())).isEqualTo(1);
    }

    @Test
    void unaOrdenManualNoSePagaEnLineaEsPAG002() throws Exception {
        JsonNode orden = orden("MANUAL");

        pagar(orden.get("token").asText())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-002"));
    }

    @Test
    void unaOrdenVencidaNoSePagaEsPAG002() throws Exception {
        JsonNode orden = ordenStripe();
        jdbc.update("UPDATE \"Ordenes\" SET \"FechaExpiracionPago\" = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE \"Id\" = ?",
                UUID.fromString(orden.get("id").asText()));

        pagar(orden.get("token").asText())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-002"));
    }

    @Test
    void unaOrdenYaPagadaNoSePagaOtraVezEsPAG002() throws Exception {
        JsonNode orden = ordenStripe();
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPago\" = 'PAGADO' WHERE \"Id\" = ?", UUID.fromString(orden.get("id").asText()));

        pagar(orden.get("token").asText())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-002"));
    }

    @Test
    void tokenAjenoEsORD404() throws Exception {
        pagar(UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    // ---- webhook ----

    @Test
    void elWebhookConfirmaElPagoDescuentaStockYRegistraElBalance() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);

        webhook(evento("payment_intent.succeeded", intento, 10000), firmar(evento("payment_intent.succeeded", intento, 10000)))
                .andExpect(status().isOk());

        String id = orden.get("id").asText();
        assertThat(estadoPago(id)).isEqualTo("PAGADO");
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Pagos\" WHERE \"OrdenId\" = ?", String.class,
                UUID.fromString(id))).isEqualTo("APROBADO");
        assertThat(disponible(id)).isEqualTo(9);
        assertThat(movimientos(id, "VENTA")).isEqualTo(1);
        assertThat(balanceVentas()).isEqualByComparingTo("100.00");
    }

    @Test
    void elMismoWebhookDosVecesNoDescuentaNiSumaDeNuevo() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);
        String cuerpo = evento("payment_intent.succeeded", intento, 10000);

        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());
        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());

        String id = orden.get("id").asText();
        assertThat(disponible(id)).isEqualTo(9);
        assertThat(movimientos(id, "VENTA")).isEqualTo(1);
        assertThat(balanceVentas()).isEqualByComparingTo("100.00");
    }

    @Test
    void unaFirmaInvalidaEs403PAG003YNoCambiaNada() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);

        webhook(evento("payment_intent.succeeded", intento, 10000), "firma-falsa")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PAG-003"));

        assertThat(estadoPago(orden.get("id").asText())).isEqualTo("PENDIENTE");
    }

    @Test
    void unMontoDistintoRechazaElPagoYLaOrdenSigueIgual() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);
        String cuerpo = evento("payment_intent.succeeded", intento, 9999);

        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());

        String id = orden.get("id").asText();
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Pagos\" WHERE \"OrdenId\" = ?", String.class,
                UUID.fromString(id))).isEqualTo("RECHAZADO");
        assertThat(estadoPago(id)).isEqualTo("PENDIENTE");
        // La reserva del checkout sigue igual: sin pago confirmado no hay venta
        assertThat(disponible(id)).isEqualTo(10);
        assertThat(movimientos(id, "VENTA")).isZero();
    }

    @Test
    void unCobroExitosoSobreOrdenAnuladaEsPAG006YNoRevive() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);
        String id = orden.get("id").asText();
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA', \"EstadoPago\" = 'ANULADO' WHERE \"Id\" = ?",
                UUID.fromString(id));
        String cuerpo = evento("payment_intent.succeeded", intento, 10000);

        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Pagos\" WHERE \"OrdenId\" = ?", String.class,
                UUID.fromString(id))).isEqualTo("APROBADO");
        assertThat(estadoPago(id)).isEqualTo("ANULADO");
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class,
                UUID.fromString(id))).isEqualTo("CANCELADA");
        assertThat(movimientos(id, "VENTA")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tienda, "PAGO_TARDIO_RECIBIDO")).isEqualTo(1);
    }

    @Test
    void unFalloDeCobroRechazaElPendiente() throws Exception {
        JsonNode orden = ordenStripe();
        String intento = iniciarYObtenerIntento(orden);
        String cuerpo = evento("payment_intent.payment_failed", intento, 10000);

        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Pagos\" WHERE \"OrdenId\" = ?", String.class,
                UUID.fromString(orden.get("id").asText()))).isEqualTo("RECHAZADO");
        assertThat(estadoPago(orden.get("id").asText())).isEqualTo("PENDIENTE");
    }

    @Test
    void unEventoQueNoEsDeCobroSeIgnoraConRespuesta200() throws Exception {
        String cuerpo = evento("charge.refunded", null, 0);

        webhook(cuerpo, firmar(cuerpo)).andExpect(status().isOk());
    }

    // ---- ayudas ----

    private ResultActions pagar(String token) throws Exception {
        return mvc.perform(post(Rutas.vitrinaOrdenes(subdominio) + "/" + token + "/pagar"));
    }

    private ResultActions webhook(String cuerpo, String firma) throws Exception {
        return mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(cuerpo).header(FIRMA, firma));
    }

    private String firmar(String cuerpo) {
        return PasarelaEnLineaFalsa.firmar(cuerpo);
    }

    private String evento(String tipo, String intento, long monto) throws Exception {
        Map<String, Object> objeto = new LinkedHashMap<>();
        if (intento != null) {
            objeto.put("id", intento);
        }
        objeto.put("amount", monto);
        return json.writeValueAsString(Map.of("type", tipo, "data", Map.of("object", objeto)));
    }

    private String iniciarYObtenerIntento(JsonNode orden) throws Exception {
        pagar(orden.get("token").asText()).andExpect(status().isOk());
        return intentoDe(orden.get("id").asText());
    }

    /** Orden pagable con Stripe, creada con el checkout real: un producto digital de 100.00. */
    private JsonNode ordenStripe() throws Exception {
        return orden("STRIPE");
    }

    private JsonNode orden(String pasarela) throws Exception {
        UUID variante = variante(new BigDecimal("100.00"));
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", pasarela);
        pedido.put("total_esperado", new BigDecimal("100.00"));
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return json.createObjectNode()
                .put("id", datos.get("orden_id").asText())
                .put("token", datos.get("token_acceso").asText());
    }

    private UUID variante(BigDecimal precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, FALSE) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 10, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private int pagos(String orden) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" WHERE \"OrdenId\" = ?", Integer.class, UUID.fromString(orden));
    }

    private String estadoPago(String orden) {
        return jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, UUID.fromString(orden));
    }

    private String intentoDe(String orden) {
        return jdbc.queryForObject("SELECT \"TransaccionId\" FROM \"Pagos\" WHERE \"OrdenId\" = ?", String.class, UUID.fromString(orden));
    }

    private int disponible(String orden) {
        return jdbc.queryForObject("SELECT i.\"CantidadDisponible\" FROM \"InventarioVariante\" i "
                + "JOIN \"ItemsOrden\" o ON o.\"VarianteId\" = i.\"VarianteId\" WHERE o.\"OrdenId\" = ?", Integer.class,
                UUID.fromString(orden));
    }

    private int movimientos(String orden, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" m JOIN \"ItemsOrden\" o "
                + "ON o.\"VarianteId\" = m.\"VarianteId\" WHERE o.\"OrdenId\" = ? AND m.\"Tipo\" = ?", Integer.class,
                UUID.fromString(orden), tipo);
    }

    private BigDecimal balanceVentas() {
        return jdbc.queryForObject("SELECT \"TotalVentasHistorico\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                BigDecimal.class, tienda);
    }
}
