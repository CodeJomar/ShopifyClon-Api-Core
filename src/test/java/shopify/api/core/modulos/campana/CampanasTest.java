package shopify.api.core.modulos.campana;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import shopify.api.core.modulos.campana.servicio.CampanaServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-028: campañas comerciales con descuento escalonado calculado por el servidor. */
class CampanasTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";

    @Autowired
    private CampanaServicio campanas;

    private UUID tienda;
    private String subdominio;
    private UUID tarifa;
    private String jwtAdmin;

    @BeforeEach
    void preparar() {
        campanas.cambiar(null);
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        jdbc.update("UPDATE \"Tiendas\" SET \"VisibleEnMercado\" = TRUE, \"EstaActiva\" = TRUE WHERE \"Id\" = ?", tienda);
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        tarifa = jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', 6.00, TRUE) RETURNING \"Id\"", UUID.class, tienda);
        UUID admin = crearUsuarioActivo(true);
        jwtAdmin = tokenPara(admin, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, admin));
    }

    @AfterEach
    void apagar() {
        campanas.cambiar(null);
    }

    private String[] producto(String precio) {
        String slug = "p-" + sufijo();
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, slug);
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, TRUE) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), new BigDecimal(precio));
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        jdbc.update("INSERT INTO \"ImagenesProducto\" (\"ProductoId\", \"UrlImagen\", \"EsPrincipal\", \"Posicion\") "
                + "VALUES (?, 'https://ejemplo.test/foto.jpg', TRUE, 1)", producto);
        return new String[]{slug, variante.toString()};
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

    private String agregar(String carrito, String variante, int cantidad) throws Exception {
        return cuerpo(enviar(post("/carrito/items"), carrito, null, Map.of("variante_id", variante, "cantidad", cantidad))
                .andExpect(status().isOk())).get("datos").get("token").asText();
    }

    private JsonNode grupoDeLaTienda(String carrito) throws Exception {
        JsonNode datos = cuerpo(enviar(get("/carrito"), carrito, null, null).andExpect(status().isOk())).get("datos");
        for (JsonNode grupo : datos.get("tiendas")) {
            if (grupo.get("tienda").get("id").asText().equals(tienda.toString())) {
                return grupo;
            }
        }
        throw new AssertionError("La tienda no aparece en el carrito");
    }

    private List<Map<String, Object>> envios() {
        return List.of(Map.of("tienda_id", tienda, "tarifa_envio_id", tarifa));
    }

    private BigDecimal cotizar(String carrito) throws Exception {
        return cuerpo(enviar(post("/carrito/cotizar"), carrito, null, Map.of("envios", envios())).andExpect(status().isOk()))
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

    private void cupon(String codigo, String tipo, int valor) {
        jdbc.update("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"EstaActivo\") VALUES (?, ?, ?, ?, 0, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE)", tienda, codigo, tipo, valor);
    }

    @Test
    void soloUnAdministradorCambiaLaCampanaYQuedaAuditada() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'CAMPANA_CAMBIADA'", Integer.class);

        putJson("/plataforma/campanas/activa", Map.of("codigo", "NAVIDAD"), tokenPara(crearUsuarioActivo(false), "u@prueba.test"), null)
                .andExpect(status().isNotFound());
        putJson("/plataforma/campanas/activa", Map.of("codigo", "NAVIDAD"), null, null).andExpect(status().isUnauthorized());

        JsonNode activa = cuerpo(putJson("/plataforma/campanas/activa", Map.of("codigo", "navidad"), jwtAdmin, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(activa.get("activa").asBoolean()).isTrue();
        assertThat(activa.get("codigo").asText()).isEqualTo("NAVIDAD");
        assertThat(activa.get("nombre").asText()).isEqualTo("Navidad");

        JsonNode publica = cuerpo(getConToken("/campanas/activa", null, null).andExpect(status().isOk())).get("datos");
        assertThat(publica.get("codigo").asText()).isEqualTo("NAVIDAD");
        assertThat(publica.get("tramos")).hasSize(4);
        assertThat(publica.get("tramos").get(1).get("desde").decimalValue()).isEqualByComparingTo("100");
        assertThat(publica.get("tramos").get(1).get("porcentaje").asInt()).isEqualTo(20);

        cuerpo(putJson("/plataforma/campanas/activa", Map.of("codigo", "INVENTADA"), jwtAdmin, null)
                .andExpect(status().isUnprocessableEntity()))
                .get("codigo");
        assertThat(cuerpo(getConToken("/campanas/activa", null, null)).get("datos").get("codigo").asText()).isEqualTo("NAVIDAD");

        JsonNode apagada = cuerpo(putJson("/plataforma/campanas/activa", new LinkedHashMap<String, Object>() {{
            put("codigo", null);
        }}, jwtAdmin, null).andExpect(status().isOk())).get("datos");
        assertThat(apagada.get("activa").asBoolean()).isFalse();
        assertThat(apagada.has("codigo")).isFalse();
        assertThat(apagada.has("nombre")).isFalse();
        assertThat(apagada.get("tramos")).hasSize(4);

        JsonNode estado = cuerpo(getConToken("/plataforma/campanas", jwtAdmin, null).andExpect(status().isOk())).get("datos");
        assertThat(estado.get("disponibles")).hasSize(5);
        assertThat(estado.has("activa")).isFalse();
        getConToken("/plataforma/campanas", tokenPara(crearUsuarioActivo(false), "u2@prueba.test"), null).andExpect(status().isNotFound());

        Integer despues = jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'CAMPANA_CAMBIADA'", Integer.class);
        assertThat(despues - antes).isGreaterThanOrEqualTo(2);
    }

    @Test
    void elMercadoYLaVitrinaMuestranPrecioFinalYElDeListaComoComparacion() throws Exception {
        String[] caro = producto("600.00");
        putJson("/plataforma/campanas/activa", Map.of("codigo", "BLACK_FRIDAY"), jwtAdmin, null).andExpect(status().isOk());

        JsonNode detalle = cuerpo(getConToken("/vitrina/" + subdominio + "/productos/" + caro[0], null, null)
                .andExpect(status().isOk())).get("datos").get("variantes").get(0);
        assertThat(detalle.get("precio").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(detalle.get("precio_comparacion").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(detalle.get("descuento_campana_porcentaje").asInt()).isEqualTo(25);
        assertThat(detalle.get("campana").asText()).isEqualTo("BLACK_FRIDAY");

        JsonNode tarjeta = cuerpo(getConToken("/vitrina/" + subdominio + "/productos", null, null).andExpect(status().isOk()))
                .get("datos").get("datos").get(0);
        assertThat(tarjeta.get("precio_minimo").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(tarjeta.get("precio_comparacion").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(tarjeta.get("campana").asText()).isEqualTo("BLACK_FRIDAY");

        JsonNode mercado = cuerpo(getConToken("/mercado/productos?tienda=" + subdominio, null, null).andExpect(status().isOk()))
                .get("datos").get("datos").get(0);
        assertThat(mercado.get("precio_desde").decimalValue()).isEqualByComparingTo("450.00");
        assertThat(mercado.get("precio_comparacion_desde").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(mercado.get("descuento_campana_porcentaje").asInt()).isEqualTo(25);

        // Sin campaña todo vuelve al precio de lista y los campos de campaña desaparecen
        putJson("/plataforma/campanas/activa", new LinkedHashMap<String, Object>() {{
            put("codigo", null);
        }}, jwtAdmin, null).andExpect(status().isOk());
        JsonNode normal = cuerpo(getConToken("/vitrina/" + subdominio + "/productos/" + caro[0], null, null)
                .andExpect(status().isOk())).get("datos").get("variantes").get(0);
        assertThat(normal.get("precio").decimalValue()).isEqualByComparingTo("600.00");
        assertThat(normal.has("campana")).isFalse();
        assertThat(normal.has("descuento_campana_porcentaje")).isFalse();
    }

    @Test
    void elCarritoLaCotizacionYElCheckoutCalculanElMismoTotalConCampana() throws Exception {
        String[] a = producto("150.00");
        String[] b = producto("50.00");
        String token = agregar(null, a[1], 2);
        agregar(token, b[1], 1);
        BigDecimal sinCampana = cotizar(token);
        assertThat(grupoDeLaTienda(token).get("subtotal").decimalValue()).isEqualByComparingTo("350.00");

        putJson("/plataforma/campanas/activa", Map.of("codigo", "NAVIDAD"), jwtAdmin, null).andExpect(status().isOk());

        JsonNode grupo = grupoDeLaTienda(token);
        // 2 x 150 con 20 % = 2 x 120 = 240; 1 x 50 con 10 % = 45; subtotal 285 y ahorro 65
        assertThat(grupo.get("subtotal").decimalValue()).isEqualByComparingTo("285.00");
        assertThat(grupo.get("ahorro_campana").decimalValue()).isEqualByComparingTo("65.00");
        JsonNode item = grupo.get("lineas").get(0);
        assertThat(item.get("precio_unitario").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(item.get("precio_comparacion").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(item.get("descuento_campana_porcentaje").asInt()).isEqualTo(20);
        assertThat(item.get("campana").asText()).isEqualTo("NAVIDAD");

        BigDecimal conCampana = cotizar(token);
        assertThat(sinCampana.subtract(conCampana)).isEqualByComparingTo("65.00");

        // El checkout recalcula igual y acepta el total cotizado (si no, respondería CHK-004)
        JsonNode pedido = cuerpo(enviar(post("/checkout"), token, UUID.randomUUID().toString(), cuerpoCheckout(conCampana))
                .andExpect(status().isCreated())).get("datos");
        assertThat(pedido.get("total").decimalValue()).isEqualByComparingTo(conCampana);
        BigDecimal precioGuardado = jdbc.queryForObject("SELECT \"PrecioUnitario\" FROM \"ItemsOrden\" WHERE \"OrdenId\" = ? "
                + "ORDER BY \"PrecioUnitario\" DESC LIMIT 1", BigDecimal.class,
                UUID.fromString(pedido.get("ordenes").get(0).get("orden_id").asText()));
        assertThat(precioGuardado).isEqualByComparingTo("120.00");

        // Un total de antes de la campaña ya no es válido
        String otro = agregar(null, a[1], 2);
        agregar(otro, b[1], 1);
        enviar(post("/checkout"), otro, UUID.randomUUID().toString(), cuerpoCheckout(sinCampana)).andExpect(status().isConflict());
    }

    @Test
    void ganaElMayorDescuentoEntreElCuponYLaCampana() throws Exception {
        cupon("MITAD", "PORCENTAJE", 50);
        cupon("CINCO", "PORCENTAJE", 5);
        String[] a = producto("150.00");
        putJson("/plataforma/campanas/activa", Map.of("codigo", "HALLOWEEN"), jwtAdmin, null).andExpect(status().isOk());
        String token = agregar(null, a[1], 2); // lista 300; campaña ahorra 60

        // El cupón del 50 % (150) supera a la campaña (60): se vende a precio de lista con el cupón
        cuerpo(enviar(put("/carrito/cupones"), token, null, Map.of("tienda_id", tienda, "codigo", "MITAD")).andExpect(status().isOk()));
        JsonNode gana = grupoDeLaTienda(token);
        assertThat(gana.get("descuento_total").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(gana.get("lineas").get(0).get("precio_unitario").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(gana.get("lineas").get(0).has("campana")).isFalse();
        assertThat(gana.get("ahorro_campana").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(codigosDeAdvertencia(gana)).doesNotContain("CAM-002");

        // El cupón del 5 % (15) pierde contra la campaña (60): el cupón no se aplica y se avisa
        cuerpo(enviar(put("/carrito/cupones"), token, null, Map.of("tienda_id", tienda, "codigo", "CINCO")).andExpect(status().isOk()));
        JsonNode pierde = grupoDeLaTienda(token);
        assertThat(pierde.get("descuento_total").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(pierde.get("lineas").get(0).get("precio_unitario").decimalValue()).isEqualByComparingTo("120.00");
        assertThat(pierde.get("lineas").get(0).get("campana").asText()).isEqualTo("HALLOWEEN");
        assertThat(pierde.get("ahorro_campana").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(codigosDeAdvertencia(pierde)).contains("CAM-002");

        // El checkout con el cupón perdedor cobra lo mismo que la cotización y no consume el uso del cupón
        BigDecimal cotizado = cotizar(token);
        cuerpo(enviar(post("/checkout"), token, UUID.randomUUID().toString(), cuerpoCheckout(cotizado)).andExpect(status().isCreated()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Ordenes\" WHERE \"TiendaId\" = ? AND \"CuponId\" IS NOT NULL",
                Integer.class, tienda)).isZero();
    }

    private static List<String> codigosDeAdvertencia(JsonNode grupo) {
        List<String> codigos = new java.util.ArrayList<>();
        grupo.path("advertencias").forEach(a -> codigos.add(a.get("codigo").asText()));
        return codigos;
    }
}
