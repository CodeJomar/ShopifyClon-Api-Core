package shopify.api.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/**
 * T-022, punto 3: un recorrido completo solo por HTTP, desde la cuenta del comerciante hasta las métricas del panel.
 * El código de activación no se guarda en la base; sale del evento que se publica al registrarse, como en las pruebas de usuario.
 */
@RecordApplicationEvents
class PuntaAPuntaTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Clave12345";

    @Autowired
    private ApplicationEvents eventos;

    @Test
    void recorridoCompletoDelComercianteAlReembolsoParcialConMetricasCoherentes() throws Exception {
        // 1. Cuenta del comerciante: registro, activación y login
        String correo = "punta-" + sufijo() + "@prueba.test";
        postJson(Rutas.AUTH_REGISTRO, cuentaRegistro(correo), null, null).andExpect(status().isCreated());
        postJson(Rutas.AUTH_ACTIVAR, Map.of("correo_electronico", correo, "codigo", codigoDe(correo)), null, null)
                .andExpect(status().isOk());
        String jwt = cuerpo(postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", correo, "contrasena", CLAVE), null, null)
                .andExpect(status().isOk())).get("datos").get("token").asText();

        // 2. Tienda: nace con prueba vigente, así que el panel puede escribir
        String subdominio = "punta-" + sufijo();
        UUID tienda = idDe(cuerpo(postJson(Rutas.CUENTA_TIENDAS, Map.of("nombre", "Punta a punta", "subdominio", subdominio,
                "correo_contacto", "contacto@prueba.test"), jwt, null).andExpect(status().isCreated())));

        // 3. Tarifa de envío, categoría y producto con dos variantes e imagen, publicado
        UUID tarifa = idDe(cuerpo(postJson(Rutas.PANEL_ENVIOS_TARIFAS, Map.of("nombre", "Estandar", "precio", "5.00"), jwt, tienda)
                .andExpect(status().isCreated())));
        UUID categoria = idDe(cuerpo(postJson(Rutas.PANEL_CATALOGO_CATEGORIAS, Map.of("nombre", "Ropa"), jwt, tienda)
                .andExpect(status().isCreated())));
        // El catálogo guarda el SKU en mayúsculas (D-013)
        String skuM = ("CAM-M-" + sufijo()).toUpperCase();
        String skuL = ("CAM-L-" + sufijo()).toUpperCase();
        UUID producto = idDe(cuerpo(postJson(Rutas.PANEL_CATALOGO_PRODUCTOS, Map.of("titulo", "Camiseta",
                "categoria_ids", List.of(categoria),
                "variantes", List.of(variante(skuM, "20.00", 5), variante(skuL, "22.00", 3))), jwt, tienda)
                .andExpect(status().isCreated())));
        postJson(Rutas.panelProductoAccion(producto, "imagenes"),
                Map.of("url_imagen", "https://cdn.prueba.test/" + sufijo() + ".jpg"), jwt, tienda)
                .andExpect(status().isCreated());
        postJson(Rutas.panelProductoAccion(producto, "publicar"), Map.of(), jwt, tienda).andExpect(status().isOk());
        postJson(Rutas.PANEL_CUPONES, Map.of("codigo", "PUNTA10", "tipo_descuento", "PORCENTAJE", "valor_descuento", 10), jwt, tienda)
                .andExpect(status().isCreated());
        UUID varianteM = varianteId(skuM);
        UUID varianteL = varianteId(skuL);

        // 4. Compra anónima: carrito con dos líneas y cupón
        String base = Rutas.vitrinaCarrito(subdominio);
        String carrito = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        agregarAlCarrito(base, carrito, varianteM, 2);
        agregarAlCarrito(base, carrito, varianteL, 1);
        mvc.perform(put(base + "/cupon").contentType(MediaType.APPLICATION_JSON).header(CARRITO, carrito)
                        .content(json.writeValueAsString(Map.of("codigo", "PUNTA10"))))
                .andExpect(status().isOk());

        // 5. Checkout con pago manual; los importes se calculan con la tasa de impuesto de la tienda
        BigDecimal tasa = cuerpo(getConToken(Rutas.PANEL_TIENDA, jwt, tienda).andExpect(status().isOk()))
                .get("datos").get("tasa_impuesto").decimalValue();
        BigDecimal subtotal = new BigDecimal("62.00");
        BigDecimal descuento = new BigDecimal("6.20");
        BigDecimal envio = new BigDecimal("5.00");
        BigDecimal baseImponible = subtotal.subtract(descuento);
        BigDecimal impuesto = baseImponible.multiply(tasa).divide(BigDecimal.ONE.add(tasa), 2, RoundingMode.HALF_UP);
        BigDecimal total = baseImponible.add(envio);

        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("direccion_envio", direccion());
        pedido.put("tarifa_envio_id", tarifa);
        pedido.put("total_esperado", total);
        JsonNode pedidoCreado = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio))
                        .contentType(MediaType.APPLICATION_JSON).header(CARRITO, carrito)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        UUID orden = UUID.fromString(pedidoCreado.get("orden_id").asText());
        String tokenAcceso = pedidoCreado.get("token_acceso").asText();
        assertThat(pedidoCreado.get("subtotal").decimalValue()).isEqualByComparingTo(subtotal);
        assertThat(pedidoCreado.get("descuento_total").decimalValue()).isEqualByComparingTo(descuento);
        assertThat(pedidoCreado.get("impuestos_total").decimalValue()).isEqualByComparingTo(impuesto);
        assertThat(pedidoCreado.get("total").decimalValue()).isEqualByComparingTo(total);

        // 6. Pago manual, envío despachado y entregado: la orden queda CERRADA
        postJson(Rutas.panelOrdenPagosManual(orden), Map.of(), jwt, tienda).andExpect(status().isCreated());
        UUID envio1 = idDe(cuerpo(postJson(Rutas.PANEL_ORDENES + "/" + orden + "/envio",
                Map.of("empresa_transporte", "Coordinadora", "numero_guia", "G-" + sufijo()), jwt, tienda)
                .andExpect(status().isCreated())));
        postJson("/panel/envios/" + envio1 + "/despachar", Map.of(), jwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + envio1 + "/entregar", Map.of(), jwt, tienda).andExpect(status().isOk());
        Map<String, Object> estados = jdbc.queryForMap("SELECT \"Estado\", \"EstadoPago\", \"EstadoPreparacion\" "
                + "FROM \"Ordenes\" WHERE \"Id\" = ?", orden);
        assertThat(estados).containsEntry("Estado", "CERRADA").containsEntry("EstadoPago", "PAGADO")
                .containsEntry("EstadoPreparacion", "ENTREGADO");

        // 7. Reembolso parcial de 10.00 que repone una unidad de la variante M
        postJson(Rutas.PANEL_ORDENES + "/" + orden + "/reembolsos", Map.of("monto", "10.00", "motivo", "Talla no disponible",
                "reponer", List.of(Map.of("variante_id", varianteM, "cantidad", 1))), jwt, tienda)
                .andExpect(status().isCreated());
        assertThat(disponible(varianteM)).isEqualTo(4);
        assertThat(disponible(varianteL)).isEqualTo(2);

        // 8. Métricas del panel, pedido en la vitrina y listado de tiendas: todos coherentes con lo anterior
        JsonNode metricas = cuerpo(getConToken(Rutas.PANEL_METRICAS_PATH, jwt, tienda).andExpect(status().isOk())).get("datos");
        assertThat(metricas.get("resumen").get("reembolsado").decimalValue()).isEqualByComparingTo("10.00");
        boolean diaConVentas = StreamSupport.stream(metricas.get("por_dia").spliterator(), false).anyMatch(dia ->
                dia.get("ventas_brutas").decimalValue().compareTo(total) == 0
                        && dia.get("reembolsos").decimalValue().compareTo(new BigDecimal("10.00")) == 0
                        && dia.get("ventas_netas").decimalValue().compareTo(total.subtract(new BigDecimal("10.00"))) == 0);
        assertThat(diaConVentas).isTrue();

        JsonNode vitrina = cuerpo(getConToken(Rutas.vitrinaOrdenes(subdominio) + "/" + tokenAcceso, null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(vitrina.get("total").decimalValue()).isEqualByComparingTo(total);

        JsonNode mis = cuerpo(getConToken(Rutas.CUENTA_TIENDAS, jwt, null).andExpect(status().isOk())).get("datos");
        assertThat(StreamSupport.stream(mis.spliterator(), false).map(t -> t.get("subdominio").asText()))
                .contains(subdominio);
    }

    private Map<String, Object> cuentaRegistro(String correo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("correo_electronico", correo);
        cuerpo.put("contrasena", CLAVE);
        cuerpo.put("nombres", "Ana");
        cuerpo.put("apellidos", "Prueba");
        return cuerpo;
    }

    private String codigoDe(String correo) {
        return eventos.stream(CodigoActivacionEmitidoEvento.class)
                .filter(e -> e.correo().equalsIgnoreCase(correo))
                .reduce((primero, segundo) -> segundo)
                .map(CodigoActivacionEmitidoEvento::codigo)
                .orElseThrow(() -> new AssertionError("No se emitió código de activación para " + correo));
    }

    private Map<String, Object> variante(String sku, String precio, int cantidadInicial) {
        Map<String, Object> variante = new LinkedHashMap<>();
        variante.put("sku", sku);
        variante.put("precio", precio);
        variante.put("cantidad_inicial", cantidadInicial);
        variante.put("requiere_envio", true);
        return variante;
    }

    private Map<String, Object> direccion() {
        Map<String, Object> direccion = new LinkedHashMap<>();
        direccion.put("nombre_contacto", "Ana Perez");
        direccion.put("direccion_linea1", "Av. Siempre Viva 123");
        direccion.put("ciudad", "Lima");
        direccion.put("estado_provincia", "Lima");
        direccion.put("codigo_postal", "15001");
        direccion.put("pais", "PE");
        return direccion;
    }

    private void agregarAlCarrito(String base, String carrito, UUID variante, int cantidad) throws Exception {
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, carrito)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", cantidad))))
                .andExpect(status().isOk());
    }

    private UUID varianteId(String sku) {
        return jdbc.queryForObject("SELECT \"Id\" FROM \"VariantesProducto\" WHERE \"SKU\" = ?", UUID.class, sku);
    }

    private int disponible(UUID variante) {
        return jdbc.queryForObject("SELECT \"CantidadDisponible\" FROM \"InventarioVariante\" WHERE \"VarianteId\" = ?",
                Integer.class, variante);
    }

    private static UUID idDe(JsonNode respuesta) {
        return UUID.fromString(respuesta.get("datos").get("id").asText());
    }
}
