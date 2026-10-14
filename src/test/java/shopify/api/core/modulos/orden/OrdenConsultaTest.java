package shopify.api.core.modulos.orden;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Consultas de órdenes: panel (ORD-011), historial del comprador (ORD-012) y estado por token (ORD-010). */
class OrdenConsultaTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    private UUID tiendaA;
    private UUID tiendaB;
    private String subA;
    private String subB;
    private String gestorA;

    @BeforeEach
    void preparar() {
        tiendaA = crearTiendaOperativa(crearUsuarioActivo(false));
        tiendaB = crearTiendaOperativa(crearUsuarioActivo(false));
        subA = subdominio(tiendaA);
        subB = subdominio(tiendaB);
        gestorA = personal(tiendaA, "GESTOR_PEDIDOS");
    }

    // ---- panel ----

    @Test
    void elPanelListaSoloLasOrdenesDeLaTienda() throws Exception {
        comprar(tiendaA, subA, null);
        comprar(tiendaA, subA, null);
        comprar(tiendaB, subB, null);

        JsonNode datos = cuerpo(listarPanel(tiendaA, "").andExpect(status().isOk())).get("datos");

        assertThat(datos.get("total").asInt()).isEqualTo(2);
        assertThat(datos.get("datos")).hasSize(2);
    }

    @Test
    void losFiltrosDeEstadoFuncionanYUnEstadoInvalidoEsVAL003() throws Exception {
        comprar(tiendaA, subA, null);

        assertThat(cuerpo(listarPanel(tiendaA, "estado=ABIERTA").andExpect(status().isOk())).get("datos").get("total").asInt())
                .isEqualTo(1);
        assertThat(cuerpo(listarPanel(tiendaA, "estado=CANCELADA").andExpect(status().isOk())).get("datos").get("total").asInt())
                .isZero();
        listarPanel(tiendaA, "estado=VOLADA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VAL-003"));
    }

    @Test
    void elTextoBuscaEnElNumeroYEscapaLosComodines() throws Exception {
        String numero = comprar(tiendaA, subA, null).numero();

        // Se busca por los dígitos del número: el '#' no viaja codificado en la URL de la prueba
        assertThat(cuerpo(listarPanel(tiendaA, "texto=" + numero.substring(1)).andExpect(status().isOk()))
                .get("datos").get("total").asInt()).isEqualTo(1);
        // El comodín % no debe coincidir con todo: se escapa y no encuentra nada
        assertThat(cuerpo(listarPanel(tiendaA, "texto=%25").andExpect(status().isOk()))
                .get("datos").get("total").asInt()).isZero();
    }

    @Test
    void elDetalleDelPanelIncluyeLaComisionYSoloDeLaTienda() throws Exception {
        UUID orden = comprar(tiendaA, subA, null).id();

        JsonNode datos = cuerpo(mvc.perform(get(Rutas.PANEL_ORDENES + "/" + orden)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorA).header("X-Tienda-Id", tiendaA.toString()))
                .andExpect(status().isOk())).get("datos");
        assertThat(datos.has("comision_plataforma")).isTrue();

        // Un gestor real de la tienda B pide una orden de la A: ORD-404 (no pertenece a su tienda)
        String gestorB = personal(tiendaB, "GESTOR_PEDIDOS");
        mvc.perform(get(Rutas.PANEL_ORDENES + "/" + orden)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorB).header("X-Tienda-Id", tiendaB.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    @Test
    void gestorDeCatalogoNoVeElPanelDePedidosEs403() throws Exception {
        comprar(tiendaA, subA, null);
        String catalogo = personal(tiendaA, "GESTOR_CATALOGO");

        mvc.perform(get(Rutas.PANEL_ORDENES).header(HttpHeaders.AUTHORIZATION, "Bearer " + catalogo)
                        .header("X-Tienda-Id", tiendaA.toString()))
                .andExpect(status().isForbidden());
    }

    // ---- historial del comprador ----

    @Test
    void elCompradorVeSusOrdenesDeDosTiendasYNoLasDeOtroUsuario() throws Exception {
        UUID comprador = crearUsuarioActivo(false);
        String jwt = jwtDe(comprador);
        comprar(tiendaA, subA, jwt);
        comprar(tiendaB, subB, jwt);
        comprar(tiendaA, subA, null);

        JsonNode datos = cuerpo(mvc.perform(get("/cuenta/ordenes").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())).get("datos");

        assertThat(datos.get("total").asInt()).isEqualTo(2);
        assertThat(datos.get("datos").get(0).has("comision_plataforma")).isFalse();
    }

    @Test
    void elHistorialSePuedeFiltrarPorUnaTienda() throws Exception {
        UUID comprador = crearUsuarioActivo(false);
        String jwt = jwtDe(comprador);
        comprar(tiendaA, subA, jwt);
        comprar(tiendaB, subB, jwt);

        JsonNode datos = cuerpo(mvc.perform(get("/cuenta/ordenes?tienda_id=" + tiendaA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())).get("datos");

        assertThat(datos.get("total").asInt()).isEqualTo(1);
    }

    @Test
    void laOrdenDeOtroUsuarioEsORD404() throws Exception {
        String jwtComprador = jwtDe(crearUsuarioActivo(false));
        UUID ajena = comprar(tiendaA, subA, jwtDe(crearUsuarioActivo(false))).id();

        mvc.perform(get("/cuenta/ordenes/" + ajena).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtComprador))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    // ---- estado público por token ----

    @Test
    void laPaginaPorTokenMuestraLaOrdenSinComision() throws Exception {
        Orden orden = comprar(tiendaA, subA, null);

        JsonNode datos = cuerpo(mvc.perform(get(Rutas.vitrina(subA) + "/ordenes/" + orden.token()))
                .andExpect(status().isOk())).get("datos");

        assertThat(datos.get("numero_orden").asText()).isEqualTo(orden.numero());
        assertThat(datos.has("comision_plataforma")).isFalse();
    }

    @Test
    void unTokenAjenoOUnaTiendaAjenaEsORD404() throws Exception {
        Orden orden = comprar(tiendaA, subA, null);

        mvc.perform(get(Rutas.vitrina(subA) + "/ordenes/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
        mvc.perform(get(Rutas.vitrina(subB) + "/ordenes/" + orden.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ORD-404"));
    }

    @Test
    void consultarNoModificaLaOrden() throws Exception {
        Orden orden = comprar(tiendaA, subA, null);
        String antes = fechaEdicion(orden.id());

        mvc.perform(get(Rutas.vitrina(subA) + "/ordenes/" + orden.token())).andExpect(status().isOk());
        listarPanel(tiendaA, "").andExpect(status().isOk());

        assertThat(fechaEdicion(orden.id())).isEqualTo(antes);
    }

    // ---- ayudas ----

    private ResultActions listarPanel(UUID tienda, String query) throws Exception {
        return mvc.perform(get(Rutas.PANEL_ORDENES + (query.isEmpty() ? "" : "?" + query))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorA)
                .header("X-Tienda-Id", tienda.toString()));
    }

    private String subdominio(UUID tienda) {
        return jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    private String personal(UUID tienda, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tienda, usuario, rol);
        return jwtDe(usuario);
    }

    private String jwtDe(UUID usuario) {
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    private String fechaEdicion(UUID orden) {
        return String.valueOf(jdbc.queryForObject("SELECT \"FechaEdicion\" FROM \"Ordenes\" WHERE \"Id\" = ?",
                java.sql.Timestamp.class, orden));
    }

    /** Orden con el checkout real; con {@code jwt} queda ligada al comprador registrado. */
    private Orden comprar(UUID tienda, String subdominio, String jwt) throws Exception {
        UUID variante = variante(tienda, new BigDecimal("20.00"));
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", new BigDecimal("20.00"));
        var peticion = post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                .content(json.writeValueAsString(pedido));
        if (jwt != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        }
        JsonNode datos = cuerpo(mvc.perform(peticion).andExpect(status().isCreated())).get("datos");
        return new Orden(UUID.fromString(datos.get("orden_id").asText()), datos.get("numero_orden").asText(),
                datos.get("token_acceso").asText());
    }

    private UUID variante(UUID tienda, BigDecimal precio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, FALSE) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private record Orden(UUID id, String numero, String token) {
    }
}
