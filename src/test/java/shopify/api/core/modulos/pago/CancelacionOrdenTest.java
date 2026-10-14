package shopify.api.core.modulos.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import shopify.api.core.modulos.pago.programador.OrdenVencimientoProgramador;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Cancelación sin pago (ORD-007), vencimiento automático (ORD-008), permisos y aislamiento por tienda. */
class CancelacionOrdenTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    @Autowired
    private OrdenVencimientoProgramador programador;

    private UUID tienda;
    private String subdominio;
    private String gestorJwt;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        gestorJwt = personal("GESTOR_PEDIDOS");
    }

    // ---- cancelación del personal ----

    @Test
    void cancelarUnaOrdenPendienteLiberaLaReservaDevuelveElCuponYAudita() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID cupon = cupon("DESC5", "5");
        UUID orden = comprar(variante, 2, "DESC5", "95.00");
        assertThat(reservada(variante)).isEqualTo(2);
        assertThat(usos(cupon)).isEqualTo(1);

        cancelar(orden, "CLIENTE", null, gestorJwt).andExpect(status().isOk());

        assertThat(estados(orden)).containsExactly("CANCELADA", "ANULADO", "NO_PREPARADO");
        assertThat(reservada(variante)).isZero();
        assertThat(disponible(variante)).isEqualTo(10);
        assertThat(movimientos(variante, "LIBERACION")).isEqualTo(1);
        assertThat(usos(cupon)).isZero();
        assertThat(auditoria("ORDEN_CANCELADA")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"MotivoCancelacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo("CLIENTE");
    }

    @Test
    void cancelarUnaOrdenPagadaSinPermisoDeReembolsoEs403SEG002() throws Exception {
        // T-019: una orden pagada se cancela reembolsando; sin REEMBOLSOS_EMITIR (GESTOR_PEDIDOS) responde 403 SEG-002
        UUID variante = variante(new BigDecimal("50.00"));
        UUID orden = comprar(variante, 1, null, "50.00");
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPago\" = 'PAGADO' WHERE \"Id\" = ?", orden);

        cancelar(orden, "CLIENTE", null, gestorJwt)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));
        assertThat(estados(orden)).containsExactly("ABIERTA", "PAGADO", "NO_PREPARADO");
        assertThat(reservada(variante)).isEqualTo(1);
    }

    @Test
    void cancelarUnaOrdenYaPreparadaEsORD007() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID orden = comprar(variante, 1, null, "50.00");
        jdbc.update("UPDATE \"Ordenes\" SET \"EstadoPreparacion\" = 'PREPARADO' WHERE \"Id\" = ?", orden);

        cancelar(orden, "SIN_STOCK", null, gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ORD-007"));
    }

    @Test
    void motivoOtroSinDetalleEsVAL001() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID orden = comprar(variante, 1, null, "50.00");

        cancelar(orden, "OTRO", null, gestorJwt)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        assertThat(estados(orden)).containsExactly("ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    @Test
    void gestorDeCatalogoNoPuedeCancelarEs403() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID orden = comprar(variante, 1, null, "50.00");

        cancelar(orden, "CLIENTE", null, personal("GESTOR_CATALOGO")).andExpect(status().isForbidden());
        assertThat(estados(orden)).containsExactly("ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    @Test
    void cancelarUnaOrdenDeOtraTiendaEsORD404() throws Exception {
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));
        UUID ajena = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\") VALUES (?, '#8001', 'c@prueba.test', 'C', 10.00, 10.00) "
                + "RETURNING \"Id\"", UUID.class, otra);

        cancelar(ajena, "CLIENTE", null, gestorJwt)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    // ---- programador de vencimientos ----

    @Test
    void elProgramadorAnulaLaVencidaYNoTocaLaQueAunNoVence() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID vencida = comprar(variante, 1, null, "50.00");
        UUID vigente = comprar(variante, 1, null, "50.00");
        jdbc.update("UPDATE \"Ordenes\" SET \"FechaExpiracionPago\" = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE \"Id\" = ?",
                vencida);

        programador.ejecutar();

        assertThat(estados(vencida)).containsExactly("CANCELADA", "ANULADO", "NO_PREPARADO");
        assertThat(jdbc.queryForObject("SELECT \"MotivoCancelacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, vencida))
                .isEqualTo("PAGO_VENCIDO");
        assertThat(estados(vigente)).containsExactly("ABIERTA", "PENDIENTE", "NO_PREPARADO");
    }

    @Test
    void ejecutarDosVecesNoHaceNadaMas() throws Exception {
        UUID variante = variante(new BigDecimal("50.00"));
        UUID vencida = comprar(variante, 1, null, "50.00");
        jdbc.update("UPDATE \"Ordenes\" SET \"FechaExpiracionPago\" = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE \"Id\" = ?",
                vencida);

        programador.ejecutar();
        programador.ejecutar();

        assertThat(auditoriaDeOrden(vencida)).isEqualTo(1);
        assertThat(movimientos(variante, "LIBERACION")).isEqualTo(1);
    }

    // ---- ayudas ----

    private ResultActions cancelar(UUID orden, String motivo, String detalle, String jwt) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        if (detalle != null) {
            cuerpo.put("detalle", detalle);
        }
        return postJson(Rutas.PANEL_ORDENES + "/" + orden + "/cancelar", cuerpo, jwt, tienda);
    }

    /** Usuario activo con el rol indicado en la tienda; devuelve su token de sesión. */
    private String personal(String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tienda, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    /** Orden creada con el checkout real: {@code cantidad} unidades de la variante, con cupón opcional. */
    private UUID comprar(UUID variante, int cantidad, String cupon, String totalEsperado) throws Exception {
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
        if (cupon != null) {
            mvc.perform(put(base + "/cupon").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                            .content(json.writeValueAsString(Map.of("codigo", cupon))))
                    .andExpect(status().isOk());
        }
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", new BigDecimal(totalEsperado));
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    private UUID cupon(String codigo, String valor) {
        return jdbc.queryForObject("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"EstaActivo\") VALUES (?, ?, 'MONTO_FIJO', ?, 0, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE) RETURNING \"Id\"", UUID.class, tienda, codigo,
                new BigDecimal(valor));
    }

    /** Variante con stock 10 en la tienda (rastrea inventario). */
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

    private java.util.List<String> estados(UUID orden) {
        return jdbc.queryForList("SELECT \"Estado\", \"EstadoPago\", \"EstadoPreparacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", orden)
                .get(0).values().stream().map(String::valueOf).toList();
    }

    private int reservada(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadReservada\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int disponible(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private int usos(UUID cupon) {
        return jdbc.queryForObject("SELECT \"CantidadUsosActual\" FROM \"Cupones\" WHERE \"Id\" = ?", Integer.class, cupon);
    }

    private int movimientos(UUID variante, String tipo) {
        return jdbc.queryForObject("SELECT count(*) FROM \"MovimientosInventario\" WHERE \"VarianteId\" = ? AND \"Tipo\" = ?",
                Integer.class, variante, tipo);
    }

    private int auditoria(String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tienda, accion);
    }

    private int auditoriaDeOrden(UUID orden) {
        return jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"EntidadId\" = ? AND \"Accion\" = ?",
                Integer.class, orden, "ORDEN_CANCELADA");
    }
}
