package shopify.api.core.modulos.pago;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Reembolsos (PAG-007, PAG-008), reposición (INV-006), balance (BAL-002), pasarela falsa y cancelación pagada (ORD-007). */
class ReembolsoTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    private UUID tienda;
    private String subdominio;
    private String admin;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        admin = personal(tienda, "ADMINISTRADOR");
    }

    // ---- reembolsos parciales y totales (PAG-007, PAG-008) ----

    @Test
    void reembolsoParcialDelSaldoYLuegoElTotalYUnTerceroEsPAG007() throws Exception {
        UUID orden = pagada(new BigDecimal("50.00"), 2, "MANUAL");

        reembolsar(orden, "60.00", List.of(), admin).andExpect(status().isCreated());
        assertThat(estadoPago(orden)).isEqualTo("PARCIALMENTE_REEMBOLSADO");

        reembolsar(orden, "40.00", List.of(), admin).andExpect(status().isCreated());
        assertThat(estadoPago(orden)).isEqualTo("REEMBOLSADO");

        reembolsar(orden, "1.00", List.of(), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-007"));
    }

    @Test
    void unReembolsoMayorQueLoReembolsableEs422ConElMaximo() throws Exception {
        UUID orden = pagada(new BigDecimal("50.00"), 2, "MANUAL");
        reembolsar(orden, "50.00", List.of(), admin).andExpect(status().isCreated());

        reembolsar(orden, "60.00", List.of(), admin)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAG-007"));
        assertThat(estadoPago(orden)).isEqualTo("PARCIALMENTE_REEMBOLSADO");
    }

    // ---- reposición de stock (INV-006) ----

    @Test
    void reponerUnaUnidadDevuelveElStockYRegistraElMovimiento() throws Exception {
        UUID variante = varianteDe(pagada(new BigDecimal("50.00"), 2, "MANUAL"));
        UUID orden = ordenDeVariante(variante);
        int antes = disponible(variante);

        reembolsar(orden, "50.00", List.of(Map.of("variante_id", variante, "cantidad", 1)), admin)
                .andExpect(status().isCreated());

        assertThat(disponible(variante)).isEqualTo(antes + 1);
        assertThat(movimientos(variante, "REPOSICION")).isEqualTo(1);
    }

    @Test
    void reponerMasDeLoVendidoEs422() throws Exception {
        UUID orden = pagada(new BigDecimal("50.00"), 2, "MANUAL");
        UUID variante = varianteDe(orden);

        reembolsar(orden, "50.00", List.of(Map.of("variante_id", variante, "cantidad", 3)), admin)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PAG-007"));
        assertThat(movimientos(variante, "REPOSICION")).isZero();
        assertThat(estadoPago(orden)).isEqualTo("PAGADO");
    }

    // ---- balance (BAL-002) ----

    @Test
    void elReembolsoRestaDelSaldoYNoDevuelveLaComision() throws Exception {
        UUID orden = pagada(new BigDecimal("100.00"), 1, "MANUAL");
        assertThat(balance("TotalComisiones")).isEqualByComparingTo("2.00");

        reembolsar(orden, "30.00", List.of(), admin).andExpect(status().isCreated());

        assertThat(balance("TotalReembolsado")).isEqualByComparingTo("30.00");
        assertThat(balance("SaldoPendienteCobro")).isEqualByComparingTo("68.00");
        assertThat(balance("TotalComisiones")).isEqualByComparingTo("2.00");
    }

    // ---- pasarela falsa (Stripe) ----

    @Test
    void unReembolsoDeStripeAprobadoBajaElEstadoDeLaOrden() throws Exception {
        UUID orden = pagadaEnStripe(new BigDecimal("100.00"));

        reembolsar(orden, "10.00", List.of(), admin).andExpect(status().isCreated());

        assertThat(estadoPago(orden)).isEqualTo("PARCIALMENTE_REEMBOLSADO");
    }

    @Test
    void unReembolsoDeStripeRechazadoDejaLaOrdenIgualYRegistraRECHAZADO() throws Exception {
        UUID orden = pagadaEnStripe(new BigDecimal("100.00"));

        reembolsar(orden, "10.13", List.of(), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-007"));

        assertThat(estadoPago(orden)).isEqualTo("PAGADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" WHERE \"OrdenId\" = ? AND \"Tipo\" = 'REEMBOLSO' "
                + "AND \"Estado\" = 'RECHAZADO'", Integer.class, orden)).isEqualTo(1);
    }

    // ---- cancelación pagada (ORD-007) ----

    @Test
    void cancelarPagadaSinPrepararReembolsaReponeStockYCancela() throws Exception {
        UUID orden = pagadaFisica(new BigDecimal("50.00"), 1);
        UUID variante = varianteDe(orden);
        int antes = disponible(variante);

        cancelar(orden, "CLIENTE", null, admin).andExpect(status().isOk());

        assertThat(estados(orden)).containsExactly("CANCELADA", "REEMBOLSADO", "NO_PREPARADO");
        assertThat(disponible(variante)).isEqualTo(antes + 1);
        assertThat(movimientos(variante, "REPOSICION")).isEqualTo(1);
    }

    @Test
    void cancelarPagadaSinRepornerStockNoMueveElInventario() throws Exception {
        UUID orden = pagadaFisica(new BigDecimal("50.00"), 1);
        UUID variante = varianteDe(orden);
        int antes = disponible(variante);

        cancelar(orden, "CLIENTE", null, admin, false).andExpect(status().isOk());

        assertThat(estados(orden)).containsExactly("CANCELADA", "REEMBOLSADO", "NO_PREPARADO");
        assertThat(disponible(variante)).isEqualTo(antes);
        assertThat(movimientos(variante, "REPOSICION")).isZero();
    }

    @Test
    void cancelarPagadaYaPreparadaEsORD007SinReembolsar() throws Exception {
        UUID orden = pagadaFisica(new BigDecimal("50.00"), 1);
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPreparacion\" = 'PREPARADO' WHERE \"Id\" = ?", orden);

        cancelar(orden, "CLIENTE", null, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORD-007"));
        assertThat(estadoPago(orden)).isEqualTo("PAGADO");
    }

    @Test
    void cancelarPagadaSinPermisoDeReembolsoEs403SEG002() throws Exception {
        UUID orden = pagadaFisica(new BigDecimal("50.00"), 1);
        String gestor = personal(tienda, "GESTOR_PEDIDOS");

        cancelar(orden, "CLIENTE", null, gestor)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
        assertThat(estadoPago(orden)).isEqualTo("PAGADO");
    }

    // ---- errores y aislamiento ----

    @Test
    void reembolsarUnaOrdenNoPagadaEsPAG007() throws Exception {
        UUID orden = ordenSinPagar(new BigDecimal("50.00"));

        reembolsar(orden, "10.00", List.of(), admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-007"));
    }

    @Test
    void reembolsarUnaOrdenDeOtraTiendaEsORD404() throws Exception {
        UUID orden = pagada(new BigDecimal("50.00"), 1, "MANUAL");
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));
        String adminOtra = personal(otra, "ADMINISTRADOR");

        postJson(Rutas.PANEL_ORDENES + "/" + orden + "/reembolsos", Map.of("monto", "10.00", "motivo", "Motivo de prueba"),
                adminOtra, otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    // ---- pago tardío (PAG-006, Q-013) ----

    @Test
    void reembolsarUnPagoTardioNoTocaStockBalanceNiEstadoDeLaOrden() throws Exception {
        UUID orden = ordenSinPagar(new BigDecimal("50.00"));
        UUID variante = varianteDe(orden);
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA', \"EstadoPago\" = 'ANULADO' WHERE \"Id\" = ?", orden);
        jdbc.update("INSERT INTO \"Pagos\" (\"OrdenId\", \"PasarelaPago\", \"Monto\", \"Moneda\", \"Estado\", \"Tipo\") "
                + "VALUES (?, 'MANUAL', 50.00, 'USD', 'APROBADO', 'CARGO')", orden);
        int disponibleAntes = disponible(variante);

        reembolsar(orden, "50.00", List.of(), admin).andExpect(status().isCreated());

        assertThat(estados(orden)).containsExactly("CANCELADA", "ANULADO", "NO_PREPARADO");
        assertThat(disponible(variante)).isEqualTo(disponibleAntes);
        assertThat(movimientos(variante, "REPOSICION")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", Integer.class, tienda))
                .isZero();
    }

    // ---- ayudas ----

    private ResultActions reembolsar(UUID orden, String monto, List<Map<String, Object>> reponer, String jwt) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("monto", new BigDecimal(monto));
        cuerpo.put("motivo", "Reembolso de prueba");
        cuerpo.put("reponer", reponer);
        return postJson(Rutas.PANEL_ORDENES + "/" + orden + "/reembolsos", cuerpo, jwt, tienda);
    }

    private ResultActions cancelar(UUID orden, String motivo, String detalle, String jwt) throws Exception {
        return cancelar(orden, motivo, detalle, jwt, true);
    }

    private ResultActions cancelar(UUID orden, String motivo, String detalle, String jwt, boolean reponerStock) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        if (detalle != null) {
            cuerpo.put("detalle", detalle);
        }
        cuerpo.put("reponer_stock", reponerStock);
        return postJson(Rutas.PANEL_ORDENES + "/" + orden + "/cancelar", cuerpo, jwt, tienda);
    }

    /** Orden por checkout y pagada con el pago manual de T-015 (cobro = precio × cantidad). */
    private UUID pagada(BigDecimal precio, int cantidad, String pasarela) throws Exception {
        UUID orden = ordenSinPagar(precio, cantidad, pasarela);
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
        return orden;
    }

    /** Orden por checkout con Stripe, marcada PAGADA con un cargo de Stripe aprobado (sin webhook). */
    private UUID pagadaEnStripe(BigDecimal precio) throws Exception {
        UUID orden = ordenSinPagar(precio, 1, "STRIPE");
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPago\" = 'PAGADO' WHERE \"Id\" = ?", orden);
        jdbc.update("INSERT INTO \"Pagos\" (\"OrdenId\", \"PasarelaPago\", \"TransaccionId\", \"Monto\", \"Moneda\", \"Estado\", "
                + "\"Tipo\") VALUES (?, 'STRIPE', ?, ?, 'USD', 'APROBADO', 'CARGO')", orden, "pi_test_" + sufijo(), precio);
        return orden;
    }

    /**
     * Orden física pagada y sin preparar: un producto que requiere envío con tarifa de 0.00, así el total no cambia.
     * Una orden digital se cierra al pagar (PAG-005) y no puede cancelarse como pagada sin preparar.
     */
    private UUID pagadaFisica(BigDecimal precio, int cantidad) throws Exception {
        UUID orden = ordenFisicaSinPagar(precio, cantidad);
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
        return orden;
    }

    private UUID ordenFisicaSinPagar(BigDecimal precio, int cantidad) throws Exception {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\", "
                + "\"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, TRUE) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        UUID tarifa = jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Sin cargo', 0.00, TRUE) RETURNING \"Id\"", UUID.class, tienda);
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombre_contacto", "Ana Perez");
        direccion.put("direccion_linea1", "Av. Siempre Viva 123");
        direccion.put("ciudad", "Lima");
        direccion.put("estado_provincia", "Lima");
        direccion.put("codigo_postal", "15001");
        direccion.put("pais", "PE");
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("direccion_envio", direccion);
        pedido.put("tarifa_envio_id", tarifa);
        pedido.put("total_esperado", precio.multiply(BigDecimal.valueOf(cantidad)));
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    private UUID ordenSinPagar(BigDecimal precio) throws Exception {
        return ordenSinPagar(precio, 1, "MANUAL");
    }

    private UUID ordenSinPagar(BigDecimal precio, int cantidad, String pasarela) throws Exception {
        UUID variante = variante(precio);
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", pasarela);
        pedido.put("total_esperado", precio.multiply(BigDecimal.valueOf(cantidad)));
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
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, FALSE) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private String personal(UUID tiendaId, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    private UUID varianteDe(UUID orden) {
        return jdbc.queryForObject("SELECT \"VarianteId\" FROM \"ItemsOrden\" WHERE \"OrdenId\" = ?", UUID.class, orden);
    }

    private UUID ordenDeVariante(UUID variante) {
        return jdbc.queryForObject("SELECT \"OrdenId\" FROM \"ItemsOrden\" WHERE \"VarianteId\" = ?", UUID.class, variante);
    }

    private String estadoPago(UUID orden) {
        return jdbc.queryForObject("SELECT \"EstadoPago\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden);
    }

    private List<String> estados(UUID orden) {
        return jdbc.queryForList("SELECT \"Estado\", \"EstadoPago\", \"EstadoPreparacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", orden)
                .get(0).values().stream().map(String::valueOf).toList();
    }

    private int disponible(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int movimientos(UUID variante, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, variante, tipo);
    }

    private BigDecimal balance(String columna) {
        return jdbc.queryForObject("SELECT \"" + columna + "\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                BigDecimal.class, tienda);
    }
}
