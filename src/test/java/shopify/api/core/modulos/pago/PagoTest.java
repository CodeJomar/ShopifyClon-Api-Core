package shopify.api.core.modulos.pago;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.pago.servicio.ConfirmacionPagoServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Pago manual (PAG-004), confirmación (PAG-005, PAG-006), permisos y aislamiento por tienda. */
class PagoTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    @Autowired
    private ConfirmacionPagoServicio confirmacion;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID tienda;
    private String subdominio;
    private String gestorJwt;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        gestorJwt = personal(tienda, "GESTOR_PEDIDOS");
    }

    // ---- pago manual ----

    @Test
    void pagoManualConEnvioPagaLaOrdenMueveStockBalanceYAudita() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        UUID variante = varianteDe(orden);
        assertThat(disponible(variante)).isEqualTo(10);
        assertThat(reservada(variante)).isEqualTo(1);

        JsonNode pago = cuerpo(pagar(orden, "OP-1", gestorJwt, tienda).andExpect(status().isCreated())).get("datos");

        assertThat(pago.get("estado").asText()).isEqualTo("APROBADO");
        assertThat(pago.get("tipo").asText()).isEqualTo("CARGO");
        assertThat(pago.get("monto").decimalValue()).isEqualByComparingTo("105.00");
        assertThat(pago.get("referencia").asText()).isEqualTo("OP-1");

        assertThat(estados(orden)).containsExactly("ABIERTA", "PAGADO", "NO_PREPARADO");
        // 2 % del plan BASICO sobre 105.00
        assertThat(comision(orden)).isEqualByComparingTo("2.10");
        // Confirmar venta: baja disponible y reservada a la vez
        assertThat(disponible(variante)).isEqualTo(9);
        assertThat(reservada(variante)).isZero();
        assertThat(movimientos(variante, "VENTA")).isEqualTo(1);
        // Balance (BAL-001): ventas, comisión, órdenes y saldo
        assertThat(balance("TotalVentasHistorico")).isEqualByComparingTo("105.00");
        assertThat(balance("TotalComisiones")).isEqualByComparingTo("2.10");
        assertThat(balance("SaldoPendienteCobro")).isEqualByComparingTo("102.90");
        assertThat(jdbc.queryForObject("SELECT \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                Integer.class, tienda)).isEqualTo(1);
        assertThat(auditoria("PAGO_MANUAL_REGISTRADO")).isEqualTo(1);
    }

    @Test
    void ordenDigitalPagadaSeEntregaYSeCierraDeInmediato() throws Exception {
        UUID orden = ordenDigital(new BigDecimal("50.00"));

        pagar(orden, null, gestorJwt, tienda).andExpect(status().isCreated());

        assertThat(estados(orden)).containsExactly("CERRADA", "PAGADO", "ENTREGADO");
        assertThat(comision(orden)).isEqualByComparingTo("1.00");
    }

    @Test
    void pagarDosVecesEsPAG004YNoCobraDeNuevo() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        pagar(orden, "OP-1", gestorJwt, tienda).andExpect(status().isCreated());

        pagar(orden, "OP-2", gestorJwt, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-004"));

        assertThat(cargos(orden)).isEqualTo(1);
        assertThat(balance("TotalVentasHistorico")).isEqualByComparingTo("105.00");
    }

    @Test
    void ordenAnuladaNoAdmitePagoManualEsPAG004() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA', \"EstadoPago\" = 'ANULADO' WHERE \"Id\" = ?", orden);

        pagar(orden, "OP-1", gestorJwt, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PAG-004"));
        assertThat(cargos(orden)).isZero();
    }

    // ---- confirmación directa (PAG-006) ----

    @Test
    void pagoQueLlegaTardeAUnaOrdenAnuladaQuedaAprobadoYAuditadoSinTocarLaOrden() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        jdbc.update("UPDATE \"Ordenes\" SET \"Estado\" = 'CANCELADA', \"EstadoPago\" = 'ANULADO' WHERE \"Id\" = ?", orden);
        UUID pago = jdbc.queryForObject("INSERT INTO \"Pagos\" (\"OrdenId\", \"PasarelaPago\", \"Monto\", \"Moneda\", "
                + "\"Estado\", \"Tipo\") VALUES (?, 'MANUAL', 105.00, 'USD', 'APROBADO', 'CARGO') RETURNING \"Id\"",
                UUID.class, orden);

        transaccion.executeWithoutResult(estado -> confirmacion.confirmar(tienda, pago));

        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Pagos\" WHERE \"Id\" = ?", String.class, pago))
                .isEqualTo("APROBADO");
        assertThat(estados(orden)).containsExactly("CANCELADA", "ANULADO", "NO_PREPARADO");
        assertThat(movimientos(varianteDe(orden), "VENTA")).isZero();
        // Sin venta confirmada no hay asiento de balance (BAL-001 no se ejecuta)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                Integer.class, tienda)).isZero();
        assertThat(auditoria("PAGO_TARDIO_RECIBIDO")).isEqualTo(1);
    }

    // ---- permisos y aislamiento ----

    @Test
    void gestorDeCatalogoNoPuedeRegistrarPagosSinPermisoDePedidosEs403() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        String catalogo = personal(tienda, "GESTOR_CATALOGO");

        pagar(orden, "OP-1", catalogo, tienda).andExpect(status().isForbidden());
        assertThat(cargos(orden)).isZero();
    }

    @Test
    void ordenDeOtraTiendaEsORD404() throws Exception {
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));
        UUID ordenAjena = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\") VALUES (?, '#7001', 'c@prueba.test', 'C', 10.00, 10.00) "
                + "RETURNING \"Id\"", UUID.class, otra);

        pagar(ordenAjena, "OP-1", gestorJwt, tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    // ---- consulta ----

    @Test
    void listaLosPagosSinDetallesDeLaPasarela() throws Exception {
        UUID orden = ordenConEnvio(new BigDecimal("100.00"));
        pagar(orden, "OP-1", gestorJwt, tienda).andExpect(status().isCreated());

        JsonNode datos = cuerpo(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(Rutas.panelOrdenPagos(orden))
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt)
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isOk())).get("datos");

        assertThat(datos).hasSize(1);
        assertThat(datos.get(0).get("estado").asText()).isEqualTo("APROBADO");
        assertThat(datos.get(0).has("detalles_respuesta")).isFalse();
    }

    // ---- ayudas ----

    private ResultActions pagar(UUID orden, String referencia, String jwt, UUID tiendaId) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        if (referencia != null) {
            cuerpo.put("referencia", referencia);
        }
        return postJson(Rutas.panelOrdenPagosManual(orden), cuerpo, jwt, tiendaId);
    }

    /** Usuario activo con el rol indicado en la tienda; devuelve su token de sesión. */
    private String personal(UUID tiendaId, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    /** Orden creada con el checkout real: una línea con envío (precio + 5.00 de tarifa, impuesto incluido). */
    private UUID ordenConEnvio(BigDecimal precio) throws Exception {
        UUID variante = variante(precio, true);
        UUID tarifa = jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', 5.00, TRUE) RETURNING \"Id\"", UUID.class, tienda);
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
        pedido.put("direccion_envio", direccion);
        pedido.put("tarifa_envio_id", tarifa);
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", precio.add(new BigDecimal("5.00")));
        return comprar(variante, pedido);
    }

    private UUID ordenDigital(BigDecimal precio) throws Exception {
        UUID variante = variante(precio, false);
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", precio);
        return comprar(variante, pedido);
    }

    private UUID comprar(UUID variante, Map<String, Object> pedido) throws Exception {
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    /** Variante con stock 10 en la tienda (rastrea inventario). */
    private UUID variante(BigDecimal precio, boolean requiereEnvio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, ?) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), precio, requiereEnvio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 10, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private UUID varianteDe(UUID orden) {
        return jdbc.queryForObject("SELECT \"VarianteId\" FROM \"ItemsOrden\" WHERE \"OrdenId\" = ?", UUID.class, orden);
    }

    private int disponible(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int reservada(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int movimientos(UUID variante, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, variante, tipo);
    }

    private int cargos(UUID orden) {
        return jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" WHERE \"OrdenId\" = ? AND \"Tipo\" = 'CARGO'",
                Integer.class, orden);
    }

    private int auditoria(String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tienda, accion);
    }

    private BigDecimal balance(String columna) {
        return jdbc.queryForObject("SELECT \"" + columna + "\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                BigDecimal.class, tienda);
    }

    private java.util.List<String> estados(UUID orden) {
        return jdbc.queryForList("SELECT \"Estado\", \"EstadoPago\", \"EstadoPreparacion\" FROM \"Ordenes\" WHERE \"Id\" = ?",
                orden).get(0).values().stream().map(String::valueOf).toList();
    }

    private BigDecimal comision(UUID orden) {
        return jdbc.queryForObject("SELECT \"ComisionPlataforma\" FROM \"Ordenes\" WHERE \"Id\" = ?", BigDecimal.class, orden);
    }
}
