package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B4: carrito global con líneas de varias tiendas bajo un mismo token. */
class CarritoGlobalTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";

    private UUID tiendaA;
    private UUID tiendaB;

    @BeforeEach
    void preparar() {
        tiendaA = crearTiendaOperativa(crearUsuarioActivo(false));
        tiendaB = crearTiendaOperativa(crearUsuarioActivo(false));
    }

    private UUID variante(UUID tienda, String precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, TRUE) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo(), new BigDecimal(precio));
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private UUID tarifa(UUID tienda, String precio) {
        return jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                + "VALUES (?, 'Estandar', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, new BigDecimal(precio));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder builder, String token, Object cuerpo) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header(CARRITO, token);
        }
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(builder);
    }

    private JsonNode agregar(String token, UUID variante, int cantidad) throws Exception {
        return cuerpo(enviar(post("/carrito/items"), token, Map.of("variante_id", variante, "cantidad", cantidad))
                .andExpect(status().isOk())).get("datos");
    }

    private JsonNode ver(String token) throws Exception {
        return cuerpo(enviar(get("/carrito"), token, null).andExpect(status().isOk())).get("datos");
    }

    @Test
    void agruparPorTiendaYSumarElTotalGeneral() throws Exception {
        UUID varA = variante(tiendaA, "10.00");
        UUID varB = variante(tiendaB, "25.00");
        String token = cuerpo(enviar(post("/carrito"), null, null).andExpect(status().isCreated()))
                .get("datos").get("token").asText();

        agregar(token, varA, 2);
        JsonNode carrito = agregar(token, varB, 1);

        assertThat(carrito.get("token").asText()).isEqualTo(token);
        assertThat(carrito.get("tiendas")).hasSize(2);
        assertThat(carrito.get("cantidad_articulos").asInt()).isEqualTo(3);
        BigDecimal suma = BigDecimal.ZERO;
        for (JsonNode grupo : carrito.get("tiendas")) {
            assertThat(grupo.get("lineas")).hasSize(1);
            assertThat(grupo.get("tienda").has("subdominio")).isTrue();
            suma = suma.add(grupo.get("total").decimalValue());
        }
        assertThat(carrito.get("total_general").decimalValue()).isEqualByComparingTo(suma);
        assertThat(carrito.get("total_general").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("45.00"));
        assertThat(ver(token).get("tiendas")).hasSize(2);
    }

    @Test
    void sinTokenSeCreaUnoNuevoYSeDevuelve() throws Exception {
        JsonNode carrito = agregar(null, variante(tiendaA, "10.00"), 1);

        String token = carrito.get("token").asText();
        assertThat(token).isNotBlank();
        assertThat(ver(token).get("tiendas")).hasSize(1);
    }

    @Test
    void unaSolaMonedaPorCarrito() throws Exception {
        jdbc.update("UPDATE \"Tiendas\" SET \"Moneda\" = 'USD' WHERE \"Id\" = ?", tiendaA);
        jdbc.update("UPDATE \"Tiendas\" SET \"Moneda\" = 'PEN' WHERE \"Id\" = ?", tiendaB);
        String token = agregar(null, variante(tiendaA, "10.00"), 1).get("token").asText();

        enviar(post("/carrito/items"), token, Map.of("variante_id", variante(tiendaB, "5.00"), "cantidad", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAR-009"));
    }

    @Test
    void fijarCantidadCeroQuitaLaLinea() throws Exception {
        UUID varA = variante(tiendaA, "10.00");
        String token = agregar(null, varA, 2).get("token").asText();

        enviar(put("/carrito/items/" + varA), token, Map.of("cantidad", 0)).andExpect(status().isOk());

        assertThat(ver(token).get("tiendas")).isEmpty();
        enviar(MockMvcRequestBuilders.delete("/carrito/items/" + varA), token, null).andExpect(status().isOk());
    }

    @Test
    void cotizaElEnvioPorTienda() throws Exception {
        UUID varA = variante(tiendaA, "10.00");
        UUID varB = variante(tiendaB, "25.00");
        UUID tarifaA = tarifa(tiendaA, "4.00");
        UUID tarifaB = tarifa(tiendaB, "6.00");
        String token = agregar(null, varA, 1).get("token").asText();
        agregar(token, varB, 1);

        JsonNode tarifas = cuerpo(enviar(get("/carrito/tarifas"), token, null).andExpect(status().isOk())).get("datos");
        assertThat(tarifas).hasSize(2);
        assertThat(tarifas.get(0).get("tarifas")).isNotEmpty();

        JsonNode sin = ver(token);
        JsonNode cotizado = cuerpo(enviar(post("/carrito/cotizar"), token, Map.of("envios", List.of(
                Map.of("tienda_id", tiendaA, "tarifa_envio_id", tarifaA),
                Map.of("tienda_id", tiendaB, "tarifa_envio_id", tarifaB)))).andExpect(status().isOk())).get("datos");

        BigDecimal envios = BigDecimal.ZERO;
        for (JsonNode grupo : cotizado.get("tiendas")) {
            envios = envios.add(grupo.get("costo_envio").decimalValue());
        }
        assertThat(envios).isEqualByComparingTo("10.00");
        assertThat(cotizado.get("total_general").decimalValue())
                .isEqualByComparingTo(sin.get("total_general").decimalValue().add(new BigDecimal("10.00")));

        enviar(post("/carrito/cotizar"), token, Map.of("envios", List.of(
                Map.of("tienda_id", UUID.randomUUID(), "tarifa_envio_id", tarifaA)))).andExpect(status().isBadRequest());
    }

    @Test
    void elCuponDeUnaTiendaSoloDescuentaEsaTienda() throws Exception {
        jdbc.update("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"EstaActivo\") VALUES (?, 'DIEZ', 'PORCENTAJE', 10, 0, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE)", tiendaA);
        String token = agregar(null, variante(tiendaA, "100.00"), 1).get("token").asText();
        agregar(token, variante(tiendaB, "100.00"), 1);

        JsonNode carrito = cuerpo(enviar(put("/carrito/cupones"), token, Map.of("tienda_id", tiendaA, "codigo", "DIEZ"))
                .andExpect(status().isOk())).get("datos");
        for (JsonNode grupo : carrito.get("tiendas")) {
            boolean esA = grupo.get("tienda").get("id").asText().equals(tiendaA.toString());
            assertThat(grupo.get("descuento_total").decimalValue())
                    .isEqualByComparingTo(esA ? new BigDecimal("10.00") : BigDecimal.ZERO);
        }

        JsonNode sinCupon = cuerpo(enviar(MockMvcRequestBuilders.delete("/carrito/cupones/" + tiendaA), token, null)
                .andExpect(status().isOk())).get("datos");
        for (JsonNode grupo : sinCupon.get("tiendas")) {
            assertThat(grupo.get("descuento_total").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    void vincularLoPasaALaPersonaYSeVeSoloConSesion() throws Exception {
        UUID usuario = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        String jwt = tokenPara(usuario, correo);
        String token = agregar(null, variante(tiendaA, "10.00"), 1).get("token").asText();
        agregar(token, variante(tiendaB, "10.00"), 1);

        enviar(post("/carrito/vincular"), token, null).andExpect(status().isUnauthorized());
        cuerpo(enviar(post("/carrito/vincular").header("Authorization", "Bearer " + jwt), token, null)
                .andExpect(status().isOk()));

        JsonNode conSesion = cuerpo(mvc.perform(get("/carrito").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())).get("datos");
        assertThat(conSesion.get("tiendas")).hasSize(2);
    }
}
