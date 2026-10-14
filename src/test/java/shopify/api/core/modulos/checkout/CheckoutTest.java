package shopify.api.core.modulos.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * Checkout público (paso a paso de {@code checkout.md}): totales, idempotencia, concurrencia, errores CHK/ENV/VAL/
 * INV/CAR, e identidad del comprador.
 */
class CheckoutTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    private UUID tienda;
    private String subdominio;
    private String carritoBase;
    private String checkoutBase;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        carritoBase = Rutas.vitrinaCarrito(subdominio);
        checkoutBase = Rutas.vitrinaCheckout(subdominio);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("UPDATE \"Tiendas\" SET \"PreciosIncluyenImpuesto\" = TRUE WHERE \"Id\" = ?", tienda);
    }

    // ---- ayudas ----

    /** Producto publicado con inventario que rastrea. {@code requiereEnvio} va en la variante. */
    private UUID variante(BigDecimal precio, boolean requiereEnvio, int stock) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, ?) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), precio, requiereEnvio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, TRUE, FALSE)", variante, stock);
        return variante;
    }

    private UUID tarifa(String precio, boolean activa) {
        return jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', ?, ?) RETURNING \"Id\"", UUID.class, tienda, new BigDecimal(precio), activa);
    }

    private UUID cupon(String codigo, String tipo, String valor) {
        return jdbc.queryForObject("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"EstaActivo\") VALUES (?, ?, ?, ?, 0, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE) RETURNING \"Id\"", UUID.class, tienda, codigo, tipo,
                new BigDecimal(valor));
    }

    private String carrito() throws Exception {
        return cuerpo(mvc.perform(post(carritoBase)).andExpect(status().isCreated())).get("datos").get("token").asText();
    }

    private void agregar(String token, UUID variante, int cantidad) throws Exception {
        enviar(post(carritoBase + "/items"), token, null, Map.of("variante_id", variante, "cantidad", cantidad))
                .andExpect(status().isOk());
    }

    private void aplicarCupon(String token, String codigo) throws Exception {
        enviar(put(carritoBase + "/cupon"), token, null, Map.of("codigo", codigo)).andExpect(status().isOk());
    }

    /** Una sola llamada a checkout: el helper ejecuta la petición una vez y devuelve el resultado. */
    private ResultActions checkout(String token, String clave, Map<String, Object> cuerpo) throws Exception {
        return enviar(post(checkoutBase), token, clave, cuerpo);
    }

    /** Envía el cuerpo JSON con {@code X-Carrito-Token} y, si se pasa, {@code Idempotency-Key}. */
    private ResultActions enviar(MockHttpServletRequestBuilder builder, String token, String clave, Object cuerpo)
            throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        if (token != null) {
            builder.header(CARRITO, token);
        }
        if (clave != null) {
            builder.header(CLAVE, clave);
        }
        return mvc.perform(builder);
    }

    private static Map<String, Object> direccion() {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombre_contacto", "Ana Perez");
        direccion.put("direccion_linea1", "Av. Siempre Viva 123");
        direccion.put("ciudad", "Lima");
        direccion.put("estado_provincia", "Lima");
        direccion.put("codigo_postal", "15001");
        direccion.put("pais", "PE");
        return direccion;
    }

    /** Cuerpo válido: con envío si {@code tarifa} no es nula. */
    private Map<String, Object> pedido(String totalEsperado, UUID tarifa) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("direccion_envio", direccion());
        if (tarifa != null) {
            pedido.put("tarifa_envio_id", tarifa);
        }
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", new BigDecimal(totalEsperado));
        return pedido;
    }

    private int ordenes() {
        return jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"TiendaId\" = ?", Integer.class, tienda);
    }

    private int reservada(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int usosCupon(UUID cupon) {
        return jdbc.queryForObject("SELECT \"CantidadUsosActual\" FROM \"Cupones\" WHERE \"Id\" = ?", Integer.class, cupon);
    }

    private int siguienteNumero() {
        return jdbc.queryForObject("SELECT \"SiguienteNumeroOrden\" FROM \"Tiendas\" WHERE \"Id\" = ?", Integer.class, tienda);
    }

    // ---- feliz y totales ----

    @Test
    void compraFelizConCuponYEnvioEImpuestoIncluidoCuadraConLaCalculadora() throws Exception {
        UUID uno = variante(new BigDecimal("100.00"), true, 10);
        UUID dos = variante(new BigDecimal("19.90"), true, 10);
        UUID envio = tarifa("5.00", true);
        UUID cupon = cupon("DIEZ", "PORCENTAJE", "10");
        String token = carrito();
        agregar(token, uno, 2);
        agregar(token, dos, 1);
        aplicarCupon(token, "DIEZ");

        // 219.90 − 21.99 (10 %) = 197.91 base; + envío 5.00 = 202.91 (el impuesto ya va incluido en el precio)
        JsonNode datos = cuerpo(checkout(token, UUID.randomUUID().toString(), pedido("202.91", envio))
                .andExpect(status().isCreated())).get("datos");

        assertThat(datos.get("numero_orden").asText()).isEqualTo("#1001");
        assertThat(datos.get("estado").asText()).isEqualTo("ABIERTA");
        assertThat(datos.get("estado_pago").asText()).isEqualTo("PENDIENTE");
        assertThat(datos.get("moneda").asText()).isEqualTo("USD");
        assertThat(datos.get("subtotal").decimalValue()).isEqualByComparingTo("219.90");
        assertThat(datos.get("descuento_total").decimalValue()).isEqualByComparingTo("21.99");
        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("5.00");
        // Impuesto incluido sobre la base 197.91: 197.91 − 197.91 / 1.18 (= 167.72) = 30.19
        assertThat(datos.get("impuestos_total").decimalValue()).isEqualByComparingTo("30.19");
        assertThat(datos.get("total").decimalValue()).isEqualByComparingTo("202.91");
        assertThat(datos.get("items")).hasSize(2);
        assertThat(datos.get("items").get(0).get("sku").asText()).startsWith("SKU-");
        assertThat(datos.get("token_acceso").asText()).isNotBlank();
        assertThat(datos.has("comision_plataforma")).isFalse();

        // Stock reservado (no descontado), un uso de cupón y carrito convertido
        assertThat(reservada(uno)).isEqualTo(2);
        assertThat(reservada(dos)).isEqualTo(1);
        assertThat(disponibleDe(uno)).isEqualTo(10);
        assertThat(usosCupon(cupon)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Carritos\" WHERE \"TokenSesion\" = ?", String.class, token))
                .isEqualTo("CONVERTIDO");

        // La siguiente compra recibe el número siguiente
        String token2 = carrito();
        agregar(token2, dos, 1);
        JsonNode segunda = cuerpo(checkout(token2, UUID.randomUUID().toString(), pedido("24.90", envio))
                .andExpect(status().isCreated())).get("datos");
        assertThat(segunda.get("numero_orden").asText()).isEqualTo("#1002");
    }

    @Test
    void impuestoNoIncluidoSeSumaAlTotal() throws Exception {
        jdbc.update("UPDATE \"Tiendas\" SET \"PreciosIncluyenImpuesto\" = FALSE WHERE \"Id\" = ?", tienda);
        UUID digital = variante(new BigDecimal("100.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);

        // 100.00 + 18 % = 118.00; sin envío porque el producto es digital
        JsonNode datos = cuerpo(checkout(token, UUID.randomUUID().toString(), pedido("118.00", null))
                .andExpect(status().isCreated())).get("datos");

        assertThat(datos.get("impuestos_total").decimalValue()).isEqualByComparingTo("18.00");
        assertThat(datos.get("total").decimalValue()).isEqualByComparingTo("118.00");
    }

    // ---- idempotencia ----

    @Test
    void lamismaClaveDosVecesDevuelveLaMismaOrdenSinReservarNiUsarElCuponOtraVez() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        UUID cupon = cupon("TREINTA", "MONTO_FIJO", "5");
        String token = carrito();
        agregar(token, digital, 2);
        aplicarCupon(token, "TREINTA");
        String clave = UUID.randomUUID().toString();
        Map<String, Object> cuerpo = pedido("55.00", null);

        JsonNode primera = cuerpo(checkout(token, clave, cuerpo).andExpect(status().isCreated())).get("datos");
        JsonNode repetida = cuerpo(checkout(token, clave, cuerpo).andExpect(status().isOk())).get("datos");

        assertThat(repetida.get("orden_id")).isEqualTo(primera.get("orden_id"));
        assertThat(repetida.get("numero_orden")).isEqualTo(primera.get("numero_orden"));
        assertThat(reservada(digital)).isEqualTo(2);
        assertThat(ordenes()).isEqualTo(1);
        assertThat(usosCupon(cupon)).isEqualTo(1);
    }

    @Test
    void lamismaClaveConOtroCarritoEsCHK006() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String clave = UUID.randomUUID().toString();
        String primero = carrito();
        agregar(primero, digital, 1);
        checkout(primero, clave, pedido("30.00", null)).andExpect(status().isCreated());

        String otro = carrito();
        agregar(otro, digital, 1);
        checkout(otro, clave, pedido("30.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHK-006"));
    }

    @Test
    void sinClaveOConClaveNoUuidEsCHK007() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);

        checkout(token, null, pedido("30.00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CHK-007"));
        checkout(token, "no-es-uuid", pedido("30.00", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CHK-007"));
        assertThat(ordenes()).isZero();
    }

    // ---- concurrencia ----

    @Test
    void dosCarritosDistintosCompranLaUltimaUnidadAlaVezYSoloUnoLaConsigue() throws Exception {
        UUID ultima = variante(new BigDecimal("40.00"), false, 1);
        String primero = carrito();
        String segundo = carrito();
        agregar(primero, ultima, 1);
        agregar(segundo, ultima, 1);

        List<String> resultados = enParalelo(
                () -> checkout(primero, UUID.randomUUID().toString(), pedido("40.00", null)),
                () -> checkout(segundo, UUID.randomUUID().toString(), pedido("40.00", null)));

        assertThat(resultados).filteredOn(r -> r.startsWith("201")).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.startsWith("409") && r.contains("\"INV-002\"")).hasSize(1);
        assertThat(reservada(ultima)).isEqualTo(1);
        assertThat(ordenes()).isEqualTo(1);
    }

    @Test
    void lamismaPeticionDosVecesAlaVezDaUnaSolaOrdenConLaMismaRespuesta() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);
        String clave = UUID.randomUUID().toString();

        List<String> resultados = enParalelo(
                () -> checkout(token, clave, pedido("30.00", null)),
                () -> checkout(token, clave, pedido("30.00", null)));

        // Una llega primero y crea la orden (201); la otra espera el bloqueo del carrito y ve la misma orden (200).
        assertThat(resultados).filteredOn(r -> r.startsWith("201")).hasSize(1);
        assertThat(resultados).filteredOn(r -> r.startsWith("200")).hasSize(1);
        List<String> ids = new ArrayList<>();
        for (String resultado : resultados) {
            ids.add(json.readTree(resultado.substring(resultado.indexOf('{'))).get("datos").get("orden_id").asText());
        }
        assertThat(ids.get(0)).isEqualTo(ids.get(1));
        assertThat(ordenes()).isEqualTo(1);
        assertThat(reservada(digital)).isEqualTo(1);
    }

    /** Ejecuta las llamadas a la vez y devuelve "estado cuerpo" de cada una. */
    @SafeVarargs
    private List<String> enParalelo(Callable<ResultActions>... llamadas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(llamadas.length);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<String>> futuros = new ArrayList<>();
            for (Callable<ResultActions> llamada : llamadas) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    MvcResult resultado = llamada.call().andReturn();
                    return resultado.getResponse().getStatus() + " " + resultado.getResponse().getContentAsString();
                }));
            }
            salida.countDown();
            List<String> resultados = new ArrayList<>();
            for (Future<String> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- total distinto, cupón agotado, líneas no vendibles, tienda inactiva ----

    @Test
    void totalDistintoEsCHK004YNoDejaNadaCreado() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        UUID cupon = cupon("TREINTA", "MONTO_FIJO", "5");
        String token = carrito();
        agregar(token, digital, 2);
        aplicarCupon(token, "TREINTA");

        JsonNode error = cuerpo(checkout(token, UUID.randomUUID().toString(), pedido("99.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHK-004")));

        assertThat(new BigDecimal(error.get("detalles").get("total_recalculado").asText())).isEqualByComparingTo("55.00");
        assertThat(ordenes()).isZero();
        assertThat(reservada(digital)).isZero();
        assertThat(usosCupon(cupon)).isZero();
        assertThat(siguienteNumero()).isEqualTo(1001);
    }

    @Test
    void cuponAgotadoEntreElCarritoYElCheckoutEsCHK005YNoDejaNadaCreado() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        UUID cupon = cupon("LIMITADO", "MONTO_FIJO", "5");
        String token = carrito();
        agregar(token, digital, 1);
        aplicarCupon(token, "LIMITADO");
        jdbc.update("UPDATE \"Cupones\" SET \"LimiteUsosTotal\" = 1, \"CantidadUsosActual\" = 1 WHERE \"Id\" = ?", cupon);

        checkout(token, UUID.randomUUID().toString(), pedido("25.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHK-005"));

        assertThat(ordenes()).isZero();
        assertThat(reservada(digital)).isZero();
        assertThat(siguienteNumero()).isEqualTo(1001);
    }

    @Test
    void todasLasLineasDespublicadasEsCHK002() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);
        jdbc.update("UPDATE \"Productos\" SET \"EstaPublicado\" = FALSE WHERE \"TiendaId\" = ?", tienda);

        checkout(token, UUID.randomUUID().toString(), pedido("30.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHK-002"));
        assertThat(ordenes()).isZero();
    }

    @Test
    void tiendaDesactivadaEsCHK001() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);
        jdbc.update("UPDATE \"Tiendas\" SET \"EstaActiva\" = FALSE WHERE \"Id\" = ?", tienda);

        checkout(token, UUID.randomUUID().toString(), pedido("30.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CHK-001"));
        assertThat(ordenes()).isZero();
    }

    @Test
    void carritoYaConvertidoConOtraClaveEsCAR008() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);
        checkout(token, UUID.randomUUID().toString(), pedido("30.00", null)).andExpect(status().isCreated());

        checkout(token, UUID.randomUUID().toString(), pedido("30.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAR-008"));
        assertThat(ordenes()).isEqualTo(1);
    }

    // ---- envío ----

    @Test
    void productoConEnvioSinTarifaOConTarifaInactivaEsENV001() throws Exception {
        UUID fisico = variante(new BigDecimal("30.00"), true, 5);
        UUID inactiva = tarifa("5.00", false);
        String token = carrito();
        agregar(token, fisico, 1);

        checkout(token, UUID.randomUUID().toString(), pedido("35.00", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-001"));
        checkout(token, UUID.randomUUID().toString(), pedido("35.00", inactiva))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-001"));
        assertThat(ordenes()).isZero();
    }

    @Test
    void productoConEnvioSinDireccionEsVAL001() throws Exception {
        UUID fisico = variante(new BigDecimal("30.00"), true, 5);
        UUID envio = tarifa("5.00", true);
        String token = carrito();
        agregar(token, fisico, 1);
        Map<String, Object> sinDireccion = pedido("35.00", envio);
        sinDireccion.remove("direccion_envio");

        checkout(token, UUID.randomUUID().toString(), sinDireccion)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        assertThat(ordenes()).isZero();
    }

    @Test
    void productoDigitalNoPideTarifaNiDireccion() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        String token = carrito();
        agregar(token, digital, 1);
        Map<String, Object> sinDireccion = pedido("30.00", null);
        sinDireccion.remove("direccion_envio");

        checkout(token, UUID.randomUUID().toString(), sinDireccion).andExpect(status().isCreated());
    }

    // ---- identidad del comprador ----

    @Test
    void compradorConSesionQuedaGuardadoEnLaOrdenYUnInvitadoNo() throws Exception {
        UUID digital = variante(new BigDecimal("30.00"), false, 5);
        UUID usuario = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        String jwt = tokenPara(usuario, correo);

        String tokenUsuario = carrito();
        agregar(tokenUsuario, digital, 1);
        JsonNode conSesion = cuerpo(enviar(post(checkoutBase).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt),
                tokenUsuario, UUID.randomUUID().toString(), pedido("30.00", null))
                .andExpect(status().isCreated())).get("datos");

        String tokenInvitado = carrito();
        agregar(tokenInvitado, digital, 1);
        JsonNode invitado = cuerpo(checkout(tokenInvitado, UUID.randomUUID().toString(), pedido("30.00", null))
                .andExpect(status().isCreated())).get("datos");

        assertThat(usuarioDe(conSesion.get("orden_id").asText())).isEqualTo(usuario);
        assertThat(usuarioDe(invitado.get("orden_id").asText())).isNull();
    }

    private UUID usuarioDe(String ordenId) {
        return jdbc.queryForObject("SELECT \"UsuarioId\" FROM \"Ordenes\" WHERE \"Id\" = ?", UUID.class, UUID.fromString(ordenId));
    }

    private int disponibleDe(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }
}
