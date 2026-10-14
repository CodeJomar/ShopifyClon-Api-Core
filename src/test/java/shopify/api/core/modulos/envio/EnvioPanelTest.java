package shopify.api.core.modulos.envio;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Envíos (ENV-003 a ENV-006) sobre órdenes creadas con el checkout y pagadas con el pago manual de T-015. */
class EnvioPanelTest extends PruebaIntegracionBase {

    private static final String CARRITO = "X-Carrito-Token";
    private static final String CLAVE = "Idempotency-Key";

    private UUID tienda;
    private String subdominio;
    private String gestorJwt;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        gestorJwt = personal(tienda, "GESTOR_PEDIDOS");
    }

    // ---- ENV-003: preparar ----

    @Test
    void crearElEnvioPreparaLaOrden() throws Exception {
        UUID orden = ordenFisicaPagada();

        JsonNode datos = cuerpo(crearEnvio(orden, "https://seguimiento.test/1", tienda, gestorJwt)
                .andExpect(status().isCreated())).get("datos");

        assertThat(datos.get("estado").asText()).isEqualTo("CREADO");
        assertThat(preparacion(orden)).isEqualTo("PREPARADO");
    }

    @Test
    void unaOrdenNoPagadaNoSePreparaEsENV003() throws Exception {
        UUID orden = ordenFisica();

        crearEnvio(orden, null, tienda, gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-003"));
        assertThat(preparacion(orden)).isEqualTo("NO_PREPARADO");
    }

    @Test
    void unaOrdenDigitalNoNecesitaEnvioEsENV003() throws Exception {
        UUID orden = ordenDigitalPagada();

        crearEnvio(orden, null, tienda, gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-003"));
    }

    @Test
    void unSegundoEnvioEsENV003() throws Exception {
        UUID orden = ordenFisicaPagada();
        crearEnvio(orden, null, tienda, gestorJwt).andExpect(status().isCreated());

        crearEnvio(orden, null, tienda, gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-003"));
    }

    @Test
    void unaUrlHttpEs422VAL001() throws Exception {
        UUID orden = ordenFisicaPagada();

        crearEnvio(orden, "http://seguimiento.test/1", tienda, gestorJwt)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        assertThat(preparacion(orden)).isEqualTo("NO_PREPARADO");
    }

    // ---- ENV-004 y ENV-005: despachar, entregar, devolver ----

    @Test
    void despacharYEntregarCierranLaOrdenPorqueYaEstaPagada() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));

        JsonNode despachado = cuerpo(accion(envio, "despachar", gestorJwt).andExpect(status().isOk())).get("datos");
        assertThat(despachado.get("estado").asText()).isEqualTo("EN_TRANSITO");
        assertThat(despachado.get("fecha_despacho").isNull()).isFalse();

        JsonNode entregado = cuerpo(accion(envio, "entregar", gestorJwt).andExpect(status().isOk())).get("datos");
        assertThat(entregado.get("estado").asText()).isEqualTo("ENTREGADO");
        assertThat(entregado.get("fecha_entrega_real").isNull()).isFalse();

        assertThat(preparacion(orden)).isEqualTo("ENTREGADO");
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                .isEqualTo("CERRADA");
    }

    @Test
    void entregarUnEnvioCreadoEsENV004() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));

        accion(envio, "entregar", gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-004"));
    }

    @Test
    void devolverUnEnvioEnTransitoLoMarcaDevuelto() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));
        accion(envio, "despachar", gestorJwt).andExpect(status().isOk());

        JsonNode datos = cuerpo(accion(envio, "devolver", gestorJwt).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("estado").asText()).isEqualTo("DEVUELTO");
    }

    // ---- ENV-006: editar ----

    @Test
    void laGuiaSeEditaMientrasNoEsteEntregado() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));

        JsonNode datos = cuerpo(editar(envio, "99-GUIA", gestorJwt).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("numero_guia").asText()).isEqualTo("99-GUIA");
    }

    @Test
    void unEnvioEntregadoNoSeEditaEsENV006() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));
        accion(envio, "despachar", gestorJwt).andExpect(status().isOk());
        accion(envio, "entregar", gestorJwt).andExpect(status().isOk());

        editar(envio, "NUEVA", gestorJwt)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ENV-006"));
    }

    // ---- permisos, aislamiento y consulta ----

    @Test
    void gestorDeCatalogoNoPuedePrepararEs403() throws Exception {
        UUID orden = ordenFisicaPagada();

        crearEnvio(orden, null, tienda, personal(tienda, "GESTOR_CATALOGO")).andExpect(status().isForbidden());
        assertThat(preparacion(orden)).isEqualTo("NO_PREPARADO");
    }

    @Test
    void unEnvioDeOtraTiendaEsENV404() throws Exception {
        UUID orden = ordenFisicaPagada();
        UUID envio = envioId(crearEnvio(orden, null, tienda, gestorJwt));
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));
        String gestorOtra = personal(otra, "GESTOR_PEDIDOS");

        accion(envio, "despachar", gestorOtra, otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENV-404"));
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Envios\" WHERE \"Id\" = ?", String.class, envio))
                .isEqualTo("CREADO");
    }

    @Test
    void consultarElEnvioDeLaOrdenYSinEnvioEsENV404() throws Exception {
        UUID conEnvio = ordenFisicaPagada();
        crearEnvio(conEnvio, null, tienda, gestorJwt).andExpect(status().isCreated());
        UUID sinEnvio = ordenFisicaPagada();

        JsonNode datos = cuerpo(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(Rutas.PANEL_ORDENES + "/" + conEnvio + "/envio")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt)
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isOk())).get("datos");
        assertThat(datos.get("estado").asText()).isEqualTo("CREADO");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(Rutas.PANEL_ORDENES + "/" + sinEnvio + "/envio")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt)
                        .header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENV-404"));
    }

    // ---- ayudas ----

    private ResultActions crearEnvio(UUID orden, String url, UUID tiendaId, String jwt) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("empresa_transporte", "Coordinadora");
        cuerpo.put("numero_guia", "G-" + sufijo());
        if (url != null) {
            cuerpo.put("url_seguimiento", url);
        }
        return postJson(Rutas.PANEL_ORDENES + "/" + orden + "/envio", cuerpo, jwt, tiendaId);
    }

    private ResultActions accion(UUID envio, String accion, String jwt) throws Exception {
        return accion(envio, accion, jwt, tienda);
    }

    private ResultActions accion(UUID envio, String accion, String jwt, UUID tiendaId) throws Exception {
        return postJson("/panel/envios/" + envio + "/" + accion, Map.of(), jwt, tiendaId);
    }

    private ResultActions editar(UUID envio, String guia, String jwt) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("empresa_transporte", "Coordinadora");
        cuerpo.put("numero_guia", guia);
        return putJson("/panel/envios/" + envio, cuerpo, jwt, tienda);
    }

    private UUID envioId(ResultActions resultado) throws Exception {
        return UUID.fromString(cuerpo(resultado.andExpect(status().isCreated())).get("datos").get("id").asText());
    }

    /** Orden con una línea que requiere envío (precio 100.00 + tarifa 5.00), pagada con el pago manual. */
    private UUID ordenFisicaPagada() throws Exception {
        UUID orden = ordenFisica();
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
        return orden;
    }

    private UUID ordenFisica() throws Exception {
        return orden(true, new BigDecimal("100.00"), new BigDecimal("105.00"));
    }

    private UUID ordenDigitalPagada() throws Exception {
        UUID orden = orden(false, new BigDecimal("50.00"), new BigDecimal("50.00"));
        mvc.perform(post(Rutas.panelOrdenPagosManual(orden)).contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gestorJwt).header("X-Tienda-Id", tienda.toString())
                        .content("{}"))
                .andExpect(status().isCreated());
        return orden;
    }

    private UUID orden(boolean conEnvio, BigDecimal precio, BigDecimal totalEsperado) throws Exception {
        UUID variante = variante(precio, conEnvio);
        String base = Rutas.vitrinaCarrito(subdominio);
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("correo_cliente", "comprador@prueba.test");
        pedido.put("nombre_cliente", "Comprador");
        if (conEnvio) {
            UUID tarifa = jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\") "
                    + "VALUES (?, 'Estandar', 5.00, TRUE) RETURNING \"Id\"", UUID.class, tienda);
            Map<String, Object> direccion = new LinkedHashMap<>();
            direccion.put("nombre_contacto", "Ana Perez");
            direccion.put("direccion_linea1", "Av. Siempre Viva 123");
            direccion.put("ciudad", "Lima");
            direccion.put("estado_provincia", "Lima");
            direccion.put("codigo_postal", "15001");
            direccion.put("pais", "PE");
            pedido.put("direccion_envio", direccion);
            pedido.put("tarifa_envio_id", tarifa);
        }
        pedido.put("pasarela", "MANUAL");
        pedido.put("total_esperado", totalEsperado);
        JsonNode datos = cuerpo(mvc.perform(post(Rutas.vitrinaCheckout(subdominio)).contentType(MediaType.APPLICATION_JSON)
                        .header(CARRITO, token).header(CLAVE, UUID.randomUUID().toString())
                        .content(json.writeValueAsString(pedido)))
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("orden_id").asText());
    }

    private UUID variante(BigDecimal precio, boolean requiereEnvio) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, ?, ?) RETURNING \"Id\"", UUID.class,
                producto, "SKU-" + sufijo(), precio, requiereEnvio);
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 10, 0, TRUE, FALSE)", variante);
        return variante;
    }

    private String personal(UUID tiendaId, String rol) {
        UUID usuario = crearUsuarioActivo(false);
        agregarPersonal(tiendaId, usuario, rol);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    private String preparacion(UUID orden) {
        return jdbc.queryForObject("SELECT \"EstadoPreparacion\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden);
    }
}
