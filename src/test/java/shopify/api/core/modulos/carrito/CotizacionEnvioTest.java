package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * A-003: {@code GET /vitrina/{subdominio}/carrito?tarifa_envio_id=} cotiza el total exacto del checkout.
 * La prueba clave es la de igualdad: el {@code total_estimado} enviado como {@code total_esperado} no da CHK-004.
 */
class CotizacionEnvioTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";

    private UUID tienda;
    private String subdominio;
    private String carritoBase;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        carritoBase = Rutas.vitrinaCarrito(subdominio);
    }

    // ---- ayudas ----

    private UUID variante(String precio, boolean requiereEnvio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, ?) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), new BigDecimal(precio), requiereEnvio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private UUID tarifa(UUID deTienda, String precio, String minimoGratis, boolean activa) {
        return jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"MontoMinimoGratis\", "
                + "\"EstaActiva\") VALUES (?, 'Estandar', ?, ?, ?) RETURNING \"Id\"", UUID.class, deTienda,
                new BigDecimal(precio), minimoGratis == null ? null : new BigDecimal(minimoGratis), activa);
    }

    private void cupon(String codigo, String tipo, String valor) {
        jdbc.update("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"EstaActivo\") VALUES (?, ?, ?, ?, 0, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE)", tienda, codigo, tipo, new BigDecimal(valor));
    }

    private String carrito() throws Exception {
        return cuerpo(mvc.perform(post(carritoBase)).andExpect(status().isCreated())).get("datos").get("token").asText();
    }

    private void agregar(String token, UUID variante, int cantidad) throws Exception {
        enviar(post(carritoBase + "/items"), token, Map.of("variante_id", variante, "cantidad", cantidad))
                .andExpect(status().isOk());
    }

    private void aplicarCupon(String token, String codigo) throws Exception {
        enviar(put(carritoBase + "/cupon"), token, Map.of("codigo", codigo)).andExpect(status().isOk());
    }

    private ResultActions enviar(MockHttpServletRequestBuilder builder, String token, Object cuerpo) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).header(CARRITO, token);
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(builder);
    }

    /** GET del carrito, con o sin {@code tarifa_envio_id}. */
    private ResultActions ver(String token, UUID tarifa) throws Exception {
        MockHttpServletRequestBuilder builder = get(carritoBase).header(CARRITO, token);
        if (tarifa != null) {
            builder.param("tarifa_envio_id", tarifa.toString());
        }
        return mvc.perform(builder);
    }

    private JsonNode datos(ResultActions resultado) throws Exception {
        return cuerpo(resultado.andExpect(status().isOk())).get("datos");
    }

    private void preciosIncluyenImpuesto(boolean incluyen) {
        jdbc.update("UPDATE \"Tiendas\" SET \"PreciosIncluyenImpuesto\" = ? WHERE \"Id\" = ?", incluyen, tienda);
    }

    // ---- compatibilidad ----

    @Test
    void sinTarifaLaRespuestaEsLaDeAntesYSinCostoDeEnvio() throws Exception {
        UUID producto = variante("10.00", true);
        String token = carrito();
        agregar(token, producto, 2);

        JsonNode datos = datos(ver(token, null));

        assertThat(datos.has("costo_envio")).isFalse();
        assertThat(datos.get("subtotal").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(datos.get("requiere_envio").asBoolean()).isTrue();
    }

    // ---- cotización con envío ----

    @Test
    void conTarifaElCostoDeEnvioEntraEnElTotal() throws Exception {
        UUID producto = variante("10.00", true);
        UUID tarifa = tarifa(tienda, "5.00", null, true);
        String token = carrito();
        agregar(token, producto, 2);

        JsonNode datos = datos(ver(token, tarifa));

        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("25.00");
    }

    @Test
    void conImpuestoNoIncluidoElImpuestoSeSumaSobreLaBaseYNoSobreElEnvio() throws Exception {
        preciosIncluyenImpuesto(false);
        UUID producto = variante("10.00", true);
        UUID tarifa = tarifa(tienda, "5.00", null, true);
        String token = carrito();
        agregar(token, producto, 2);

        JsonNode datos = datos(ver(token, tarifa));

        // base 20.00; impuesto 18 % solo sobre la base = 3.60 (el envío no lleva impuesto, D-012); total 20 + 5 + 3.60
        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(datos.get("impuestos_total").decimalValue()).isEqualByComparingTo("3.60");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("28.60");
    }

    @Test
    void envioGratisCuandoLaBaseSinDescuentoAlcanzaElMinimo() throws Exception {
        UUID producto = variante("10.00", true);
        UUID tarifa = tarifa(tienda, "5.00", "15.00", true);
        String token = carrito();
        agregar(token, producto, 2);

        JsonNode datos = datos(ver(token, tarifa));

        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("20.00");
    }

    @Test
    void elMinimoGratisSeEvaluaSobreLaBaseDespuesDelDescuento() throws Exception {
        UUID producto = variante("10.00", true);
        UUID tarifa = tarifa(tienda, "5.00", "15.00", true);
        cupon("MITAD", "PORCENTAJE", "50");
        String token = carrito();
        agregar(token, producto, 2);
        aplicarCupon(token, "MITAD");

        // 20.00 − 10.00 (50 %) = 10.00 < 15.00: el envío se cobra
        JsonNode datos = datos(ver(token, tarifa));

        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("15.00");
    }

    @Test
    void sinLineasQueRequieranEnvioElParametroSeIgnora() throws Exception {
        UUID digital = variante("10.00", false);
        UUID inactiva = tarifa(tienda, "5.00", null, false);
        String token = carrito();
        agregar(token, digital, 1);

        // Ni siquiera se valida la tarifa: no hay nada que enviar
        JsonNode datos = datos(ver(token, inactiva));
        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(datos.get("requiere_envio").asBoolean()).isFalse();

        // Y un UUID cualquiera tampoco molesta
        JsonNode otra = datos(ver(token, UUID.randomUUID()));
        assertThat(otra.get("total_estimado").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void carritoVacioConTarifaDaCostoCero() throws Exception {
        UUID tarifa = tarifa(tienda, "5.00", null, true);
        String token = carrito();

        JsonNode datos = datos(ver(token, tarifa));

        assertThat(datos.get("costo_envio").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(datos.get("total_estimado").decimalValue()).isEqualByComparingTo("0.00");
    }

    // ---- tarifa inválida ----

    @Test
    void tarifaInactivaResponde409Env001() throws Exception {
        UUID producto = variante("10.00", true);
        UUID inactiva = tarifa(tienda, "5.00", null, false);
        String token = carrito();
        agregar(token, producto, 1);

        ver(token, inactiva).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ENV-001"));
    }

    @Test
    void tarifaDeOtraTiendaResponde409Env001() throws Exception {
        UUID producto = variante("10.00", true);
        UUID otraTienda = crearTiendaOperativa(crearUsuarioActivo(false));
        UUID ajena = tarifa(otraTienda, "5.00", null, true);
        String token = carrito();
        agregar(token, producto, 1);

        ver(token, ajena).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ENV-001"));
    }

    @Test
    void tarifaInexistenteResponde409Env001() throws Exception {
        UUID producto = variante("10.00", true);
        String token = carrito();
        agregar(token, producto, 1);

        ver(token, UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ENV-001"));
    }

    @Test
    void tarifaConFormatoInvalidoResponde400() throws Exception {
        String token = carrito();

        mvc.perform(get(carritoBase).header(CARRITO, token).param("tarifa_envio_id", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VAL-003"));
    }

    // ---- igualdad cotización = checkout ----

    @ParameterizedTest(name = "precios incluyen impuesto = {0}")
    @ValueSource(booleans = {true, false})
    void elTotalEstimadoConEnvioEsElQueAceptaElCheckoutSinChk004(boolean incluyen) throws Exception {
        preciosIncluyenImpuesto(incluyen);
        UUID uno = variante("100.00", true);
        UUID dos = variante("19.90", true);
        UUID digital = variante("7.35", false);
        UUID tarifa = tarifa(tienda, "5.55", null, true);
        cupon("DIEZ", "PORCENTAJE", "10");
        String token = carrito();
        agregar(token, uno, 2);
        agregar(token, dos, 3);
        agregar(token, digital, 1);
        aplicarCupon(token, "DIEZ");

        JsonNode cotizacion = datos(ver(token, tarifa));
        BigDecimal totalEstimado = cotizacion.get("total_estimado").decimalValue();
        assertThat(cotizacion.get("costo_envio").decimalValue()).isEqualByComparingTo("5.55");
        // sin el envío el total sería menor: la cotización de verdad lo incluye
        assertThat(totalEstimado).isGreaterThan(datos(ver(token, null)).get("total_estimado").decimalValue());

        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("direccion_envio", Map.of("nombre_contacto", "Ana Perez", "direccion_linea1", "Av. Siempre Viva 123",
                "ciudad", "Lima", "estado_provincia", "Lima", "codigo_postal", "15001", "pais", "PE"));
        pedido.put("tarifa_envio_id", tarifa);
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", totalEstimado);

        // Debe crear la orden a la primera: sin CHK-004
        JsonNode orden = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");

        assertThat(orden.get("total").decimalValue()).isEqualByComparingTo(totalEstimado);
        assertThat(orden.get("costo_envio").decimalValue()).isEqualByComparingTo(cotizacion.get("costo_envio").decimalValue());
        assertThat(orden.get("impuestos_total").decimalValue()).isEqualByComparingTo(cotizacion.get("impuestos_total").decimalValue());
        assertThat(orden.get("descuento_total").decimalValue()).isEqualByComparingTo(cotizacion.get("descuento_total").decimalValue());
    }
}
