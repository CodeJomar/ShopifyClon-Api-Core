package shopify.api.core.modulos.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Métricas del panel (MET-001) y comisiones de plataforma. Solo lectura. */
class MetricasTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private UUID tienda;
    private String subdominio;
    private String admin;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        admin = personal(tienda, "ADMINISTRADOR");
    }

    // ---- panel: cifras del día (criterio 1) ----

    @Test
    void dosOrdenesPagadasHoyYUnReembolsoParcialDanLasCifrasDelDiaYCuadranConElBalance() throws Exception {
        UUID primera = pagada(new BigDecimal("40.00"), "PRODUCTO A");
        pagada(new BigDecimal("60.00"), "PRODUCTO B");
        reembolsar(primera, "10.00").andExpect(status().isCreated());
        String hoy = LocalDate.now(LIMA).toString();

        JsonNode datos = metricas("desde=" + hoy + "&hasta=" + hoy);
        JsonNode dia = datos.get("por_dia").get(0);

        assertThat(dia.get("ventas_brutas").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(dia.get("reembolsos").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(dia.get("ventas_netas").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(dia.get("ordenes_pagadas").asInt()).isEqualTo(2);
        assertThat(dia.get("ticket_promedio").decimalValue()).isEqualByComparingTo("50.00");
        // El resumen coincide con BalancesTienda
        JsonNode resumen = datos.get("resumen");
        assertThat(resumen.get("ventas_historicas").decimalValue()).isEqualByComparingTo(balance("TotalVentasHistorico"));
        assertThat(resumen.get("reembolsado").decimalValue()).isEqualByComparingTo(balance("TotalReembolsado"));
        assertThat(resumen.get("saldo_por_cobrar").decimalValue()).isEqualByComparingTo(balance("SaldoPendienteCobro"));
    }

    // ---- zona horaria (criterio 2) ----

    @Test
    void unPagoALas2330DeLimaCaeEnEseDiaDeLimaYNoEnElSiguienteEnUtc() throws Exception {
        pagadaEn("2026-10-14 23:30:00-05", new BigDecimal("25.00"));

        JsonNode mismoDia = metricas("desde=2026-10-14&hasta=2026-10-14");
        assertThat(mismoDia.get("por_dia").get(0).get("ventas_brutas").decimalValue()).isEqualByComparingTo("25.00");

        JsonNode diaSiguiente = metricas("desde=2026-10-15&hasta=2026-10-15");
        assertThat(diaSiguiente.get("por_dia").get(0).get("ventas_brutas").decimalValue()).isEqualByComparingTo("0.00");
    }

    // ---- rangos y días sin ventas (criterio 3) ----

    @Test
    void unRangoDe400DiasEs422MET001() throws Exception {
        mvc.perform(get(Rutas.PANEL_METRICAS_PATH + "?desde=2025-01-01&hasta=2026-02-05")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MET-001"));
    }

    @Test
    void desdeMayorQueHastaEs422MET001() throws Exception {
        mvc.perform(get(Rutas.PANEL_METRICAS_PATH + "?desde=2026-10-10&hasta=2026-10-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("MET-001"));
    }

    @Test
    void losDiasSinVentasAparecenConCeros() throws Exception {
        pagadaEn("2026-10-02 12:00:00-05", new BigDecimal("30.00"));

        JsonNode datos = metricas("desde=2026-10-01&hasta=2026-10-03");

        assertThat(datos.get("por_dia")).hasSize(3);
        assertThat(datos.get("por_dia").get(0).get("ventas_brutas").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(datos.get("por_dia").get(1).get("ventas_brutas").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(datos.get("por_dia").get(2).get("ticket_promedio").decimalValue()).isEqualByComparingTo("0.00");
    }

    // ---- top de productos (criterio 4) ----

    @Test
    void elTopOrdenaPorUnidadesYUnaOrdenCanceladaNoCuenta() throws Exception {
        UUID varianteA = variante(new BigDecimal("10.00"), "PRODUCTO A");
        UUID varianteB = variante(new BigDecimal("10.00"), "PRODUCTO B");
        UUID varianteC = variante(new BigDecimal("10.00"), "PRODUCTO C");
        pagadaCon(varianteA, 3);
        pagadaCon(varianteB, 1);
        UUID cancelada = pagadaCon(varianteC, 9);
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA' WHERE \"Id\" = ?", cancelada);
        String hoy = LocalDate.now(LIMA).toString();

        JsonNode top = metricas("desde=" + hoy + "&hasta=" + hoy).get("top_productos");

        assertThat(top).hasSize(2);
        assertThat(top.get(0).get("variante_id").asText()).isEqualTo(varianteA.toString());
        assertThat(top.get(0).get("unidades").asLong()).isEqualTo(3L);
        assertThat(top.get(1).get("variante_id").asText()).isEqualTo(varianteB.toString());
    }

    // ---- órdenes por preparar ----

    @Test
    void cuentaLasOrdenesPagadasSinPrepararQueRequierenEnvio() throws Exception {
        pagadaFisicaSinPreparar(new BigDecimal("15.00"));

        assertThat(metricas("").get("ordenes_por_preparar").asLong()).isEqualTo(1L);
    }

    // ---- permisos (criterio 5) ----

    @Test
    void gestorDePedidosNoVeLasMetricasEs403() throws Exception {
        String gestor = personal(tienda, "GESTOR_PEDIDOS");

        mvc.perform(get(Rutas.PANEL_METRICAS_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + gestor)
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
    }

    @Test
    void unNoAdministradorDePlataformaNoVeLasComisionesEs404SEG006() throws Exception {
        mvc.perform(get("/plataforma/metricas/comisiones").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-006"));
    }

    @Test
    void elAdministradorDePlataformaVeLasComisionesDeMayorAMenor() throws Exception {
        pagada(new BigDecimal("100.00"), "PRODUCTO A");
        String plataforma = tokenPara(crearUsuarioActivo(true), "plataforma-" + sufijo() + "@prueba.test");

        JsonNode datos = cuerpo(mvc.perform(get("/plataforma/metricas/comisiones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plataforma))
                .andExpect(status().isOk())).get("datos");

        JsonNode filas = datos.get("datos");
        assertThat(filas.size()).isGreaterThan(0);
        for (int i = 1; i < filas.size(); i++) {
            assertThat(filas.get(i - 1).get("total_comisiones").decimalValue())
                    .isGreaterThanOrEqualTo(filas.get(i).get("total_comisiones").decimalValue());
        }
    }

    // ---- ayudas ----

    private JsonNode metricas(String query) throws Exception {
        String ruta = Rutas.PANEL_METRICAS_PATH + (query.isEmpty() ? "" : "?" + query);
        return cuerpo(mvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isOk())).get("datos");
    }

    private org.springframework.test.web.servlet.ResultActions reembolsar(UUID orden, String monto) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("monto", new BigDecimal(monto));
        cuerpo.put("motivo", "Reembolso de prueba");
        return postJson(Rutas.PANEL_ORDENES + "/" + orden + "/reembolsos", cuerpo, admin, tienda);
    }

    /** Orden digital de un producto, pagada hoy con el pago manual. */
    private UUID pagada(BigDecimal precio, String titulo) throws Exception {
        return pagadaCon(variante(precio, titulo), 1);
    }

    private UUID pagadaCon(UUID variante, int cantidad) throws Exception {
        UUID orden = ordenSinPagar(variante, cantidad);
        pagarManual(orden);
        return orden;
    }

    /** Orden pagada con el cobro fechado en un instante concreto (para probar la zona horaria). */
    private void pagadaEn(String instante, BigDecimal precio) throws Exception {
        UUID orden = pagada(precio, "PRODUCTO FECHADO");
        jdbc.update("UPDATE \"Pagos\" SET \"FechaCreacion\" = CAST(? AS timestamptz) WHERE \"OrdenId\" = ? AND \"Tipo\" = 'CARGO'",
                instante, orden);
    }

    private void pagadaFisicaSinPreparar(BigDecimal precio) throws Exception {
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
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
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
        pedido.put("total_esperado", precio);
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        pagarManual(UUID.fromString(datos.get("orden_id").asText()));
    }

    private UUID ordenSinPagar(UUID variante, int cantidad) throws Exception {
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
        BigDecimal total = jdbc.queryForObject("SELECT \"Precio\" FROM \"VariantesProducto\" WHERE \"Id\" = ?", BigDecimal.class,
                variante).multiply(BigDecimal.valueOf(cantidad));
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", total);
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    private void pagarManual(UUID orden) throws Exception {
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    /** Producto digital con su inventario (stock 50). */
    private UUID variante(BigDecimal precio, String titulo) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, ?, ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, titulo, "p-" + sufijo());
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

    private BigDecimal balance(String columna) {
        return jdbc.queryForObject("SELECT \"" + columna + "\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                BigDecimal.class, tienda);
    }
}
