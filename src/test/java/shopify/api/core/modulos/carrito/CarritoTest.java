package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Carrito público (CAR-001 a CAR-009, D-018, D-019). */
class CarritoTest extends PruebaIntegracionBase {

    private static final String ENCABEZADO = "X-Carrito-Token";

    private UUID tienda;
    private String base;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        base = Rutas.vitrina(subdominio) + "/carrito";
    }

    // ---- ayudas ----

    /** Variante vendible con su inventario; {@code rastrea}/{@code ventaSinStock} configuran el inventario. */
    private UUID producto(UUID tiendaId, String precio, int stock) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tiendaId, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, ?) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo(),
                new BigDecimal(precio));
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, TRUE, FALSE)", variante, stock);
        return variante;
    }

    private UUID productoDe(UUID variante) {
        return jdbc.queryForObject("SELECT \"ProductoId\" FROM \"VariantesProducto\" WHERE \"Id\" = ?", UUID.class, variante);
    }

    private ResultActions conToken(MockHttpServletRequestBuilder peticion, String token) throws Exception {
        if (token != null) {
            peticion.header(ENCABEZADO, token);
        }
        return mvc.perform(peticion);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, Object cuerpo, String token) throws Exception {
        peticion.contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            peticion.content(json.writeValueAsString(cuerpo));
        }
        return conToken(peticion, token);
    }

    private String crearCarrito() throws Exception {
        JsonNode datos = cuerpo(enviar(post(base), null, null).andExpect(status().isCreated())).get("datos");
        return datos.get("token").asText();
    }

    private ResultActions agregar(String token, UUID variante, int cantidad) throws Exception {
        return enviar(post(base + "/items"), Map.of("variante_id", variante, "cantidad", cantidad), token);
    }

    private JsonNode ver(String token) throws Exception {
        return cuerpo(conToken(get(base), token).andExpect(status().isOk())).get("datos");
    }

    private JsonNode fijar(String token, UUID variante, int cantidad) throws Exception {
        return cuerpo(enviar(put(base + "/items/" + variante), Map.of("cantidad", cantidad), token)
                .andExpect(status().isOk())).get("datos");
    }

    private void cupon(String token, String codigo, int esperado) throws Exception {
        enviar(put(base + "/cupon"), Map.of("codigo", codigo), token).andExpect(status().is(esperado));
    }

    private UUID cuponDe(String codigo, String tipo, String valor, String minimo, boolean vencido) {
        return jdbc.queryForObject("INSERT INTO \"Cupones\" (\"TiendaId\", \"Codigo\", \"TipoDescuento\", \"ValorDescuento\", "
                + "\"MontoMinimoCompra\", \"FechaInicio\", \"FechaFin\", \"EstaActivo\") VALUES (?, ?, ?, ?, ?, "
                + "CURRENT_TIMESTAMP - INTERVAL '2 days', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, codigo, tipo,
                new BigDecimal(valor), new BigDecimal(minimo),
                vencido ? java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(1)) : null);
    }

    private static BigDecimal dinero(JsonNode nodo, String campo) {
        return new BigDecimal(nodo.get(campo).asText());
    }

    private static List<String> codigos(JsonNode datos) {
        List<String> codigos = new ArrayList<>();
        datos.get("advertencias").forEach(a -> codigos.add(a.get("codigo").asText()));
        return codigos;
    }

    // ---- totales ----

    @Test
    void creaAgregaCambiaYQuitaConTotalesCorrectosConPreciosIncluidos() throws Exception {
        String token = crearCarrito();
        UUID uno = producto(tienda, "100.00", 10);
        UUID dos = producto(tienda, "19.90", 10);
        agregar(token, uno, 2).andExpect(status().isOk());
        agregar(token, dos, 1).andExpect(status().isOk());

        JsonNode carrito = ver(token);
        assertThat(dinero(carrito, "subtotal")).isEqualByComparingTo("219.90");
        assertThat(dinero(carrito, "total_estimado")).isEqualByComparingTo("219.90");
        assertThat(dinero(carrito, "impuestos_total")).isEqualByComparingTo("33.54");

        fijar(token, uno, 3);
        assertThat(dinero(ver(token), "subtotal")).isEqualByComparingTo("319.90");
        assertThat(dinero(fijar(token, dos, 0), "subtotal")).isEqualByComparingTo("300.00");

        cuerpo(conToken(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(base + "/items/" + uno), token).andExpect(status().isOk()));
        JsonNode vacio = ver(token);
        assertThat(vacio.get("items")).isEmpty();
        assertThat(dinero(vacio, "total_estimado")).isEqualByComparingTo("0.00");
    }

    @Test
    void impuestoNoIncluidoSeSumaAlTotal() throws Exception {
        jdbc.update("UPDATE \"Tiendas\" SET \"PreciosIncluyenImpuesto\" = FALSE WHERE \"Id\" = ?", tienda);
        String token = crearCarrito();
        agregar(token, producto(tienda, "100.00", 5), 1).andExpect(status().isOk());

        JsonNode carrito = ver(token);
        assertThat(dinero(carrito, "impuestos_total")).isEqualByComparingTo("18.00");
        assertThat(dinero(carrito, "total_estimado")).isEqualByComparingTo("118.00");
    }

    @Test
    void elPrecioSeRecalculaConElPrecioActual() throws Exception {
        String token = crearCarrito();
        UUID variante = producto(tienda, "50.00", 5);
        agregar(token, variante, 1).andExpect(status().isOk());

        jdbc.update("UPDATE \"VariantesProducto\" SET \"Precio\" = 60.00 WHERE \"Id\" = ?", variante);

        JsonNode carrito = ver(token);
        assertThat(dinero(carrito, "subtotal")).isEqualByComparingTo("60.00");
        assertThat(dinero(carrito.get("items").get(0), "precio_unitario")).isEqualByComparingTo("60.00");
    }

    @Test
    void despublicarDejaLaLineaFueraDelTotalConAdvertencia() throws Exception {
        String token = crearCarrito();
        UUID retirada = producto(tienda, "100.00", 5);
        UUID vigente = producto(tienda, "50.00", 5);
        agregar(token, retirada, 1).andExpect(status().isOk());
        agregar(token, vigente, 1).andExpect(status().isOk());

        jdbc.update("UPDATE \"Productos\" SET \"EstaPublicado\" = FALSE WHERE \"Id\" = ?", productoDe(retirada));

        JsonNode carrito = ver(token);
        assertThat(dinero(carrito, "subtotal")).isEqualByComparingTo("50.00");
        assertThat(codigos(carrito)).contains("CAR-006");
    }

    @Test
    void pedirMasQueElVendibleAvisaSinFallar() throws Exception {
        String token = crearCarrito();
        UUID variante = producto(tienda, "10.00", 3);

        JsonNode carrito = cuerpo(agregar(token, variante, 5).andExpect(status().isOk())).get("datos");

        assertThat(codigos(carrito)).contains("CAR-004");
        assertThat(dinero(carrito, "subtotal")).isEqualByComparingTo("50.00");
    }

    // ---- cupones ----

    @Test
    void cuponPorcentajeDescuentaSobreElSubtotal() throws Exception {
        cuponDe("DIEZ", "PORCENTAJE", "10", "0", false);
        String token = crearCarrito();
        agregar(token, producto(tienda, "200.00", 5), 1).andExpect(status().isOk());

        cupon(token, "diez", 200);
        JsonNode carrito = ver(token);

        assertThat(carrito.get("cupon").get("codigo").asText()).isEqualTo("DIEZ");
        assertThat(dinero(carrito.get("cupon"), "descuento")).isEqualByComparingTo("20.00");
        assertThat(dinero(carrito, "total_estimado")).isEqualByComparingTo("180.00");
    }

    @Test
    void cuponVencidoEs422CUP003() throws Exception {
        cuponDe("VENCIDO", "PORCENTAJE", "10", "0", true);
        String token = crearCarrito();
        agregar(token, producto(tienda, "200.00", 5), 1).andExpect(status().isOk());

        enviar(put(base + "/cupon"), Map.of("codigo", "VENCIDO"), token)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CUP-003"));
    }

    @Test
    void cuponQueDejaDeAplicarAvisaYNoDescuenta() throws Exception {
        cuponDe("MINIMO", "MONTO_FIJO", "20", "150", false);
        String token = crearCarrito();
        UUID a = producto(tienda, "100.00", 5);
        UUID b = producto(tienda, "60.00", 5);
        agregar(token, a, 1).andExpect(status().isOk());
        agregar(token, b, 1).andExpect(status().isOk());
        cupon(token, "MINIMO", 200);

        JsonNode carrito = fijar(token, b, 0);

        assertThat(codigos(carrito)).contains("CUP-003");
        assertThat(dinero(carrito.get("cupon"), "descuento")).isEqualByComparingTo("0.00");
        assertThat(carrito.get("advertencias").get(0).get("mensaje").asText()).startsWith("Compra").endsWith("150.00");
    }

    // ---- errores ----

    @Test
    void varianteDeOtraTiendaEs422CAR002() throws Exception {
        String token = crearCarrito();
        UUID ajena = producto(crearTiendaOperativa(crearUsuarioActivo(false)), "10.00", 5);

        agregar(token, ajena, 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAR-002"));
    }

    @Test
    void cincuentaYUnaLineasEs422CAR003() throws Exception {
        String token = crearCarrito();
        for (int i = 0; i < 50; i++) {
            agregar(token, producto(tienda, "1.00", 5), 1).andExpect(status().isOk());
        }
        agregar(token, producto(tienda, "1.00", 5), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAR-003"));
    }

    @Test
    void sumarMasDe99EsCAR003() throws Exception {
        String token = crearCarrito();
        UUID variante = producto(tienda, "1.00", 500);
        agregar(token, variante, 60).andExpect(status().isOk());

        agregar(token, variante, 60)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CAR-003"));
    }

    @Test
    void cantidadCienEs422() throws Exception {
        String token = crearCarrito();
        agregar(token, producto(tienda, "1.00", 5), 100)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tokenDeOtraTiendaEs404CAR404() throws Exception {
        String ajeno = crearCarritoEnOtraTienda();

        conToken(get(base), ajeno)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAR-404"));
    }

    private String crearCarritoEnOtraTienda() throws Exception {
        UUID otra = crearTiendaOperativa(crearUsuarioActivo(false));
        String sub = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, otra);
        JsonNode datos = cuerpo(enviar(post(Rutas.vitrina(sub) + "/carrito"), null, null)
                .andExpect(status().isCreated())).get("datos");
        return datos.get("token").asText();
    }

    @Test
    void tiendaDesactivadaEs404TIE404() throws Exception {
        String token = crearCarrito();
        jdbc.update("UPDATE \"Tiendas\" SET \"EstaActiva\" = FALSE WHERE \"Id\" = ?", tienda);

        conToken(get(base), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
    }

    @Test
    void carritoConvertidoNoSeModificaCAR008() throws Exception {
        String token = crearCarrito();
        jdbc.update("UPDATE \"Carritos\" SET \"Estado\" = 'CONVERTIDO' WHERE \"TokenSesion\" = ?", token);

        agregar(token, producto(tienda, "1.00", 5), 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAR-008"));
    }

    // ---- vinculación (D-019) ----

    private ResultActions vincular(String token, String jwt) throws Exception {
        MockHttpServletRequestBuilder peticion = post(base + "/vincular");
        if (jwt != null) {
            peticion.header("Authorization", "Bearer " + jwt);
        }
        return conToken(peticion, token);
    }

    private String sesion(UUID usuario) {
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        return tokenPara(usuario, correo);
    }

    @Test
    void vincularSinSesionEs401() throws Exception {
        String token = crearCarrito();
        vincular(token, null).andExpect(status().isUnauthorized());
    }

    @Test
    void vincularSinCarritoPrevioAsignaElCarritoAlUsuario() throws Exception {
        String token = crearCarrito();
        UUID variante = producto(tienda, "30.00", 5);
        agregar(token, variante, 2).andExpect(status().isOk());
        UUID usuario = crearUsuarioActivo(false);

        JsonNode carrito = cuerpo(vincular(token, sesion(usuario)).andExpect(status().isOk())).get("datos");

        assertThat(carrito.get("token").asText()).isEqualTo(token);
        assertThat(carrito.get("items").get(0).get("cantidad").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT \"UsuarioId\" FROM \"Carritos\" WHERE \"TokenSesion\" = ?", UUID.class, token))
                .isEqualTo(usuario);
    }

    @Test
    void vincularConCarritoPrevioFusionaSumandoConTopeYMarcaElDeInvitadoConvertido() throws Exception {
        UUID usuario = crearUsuarioActivo(false);
        String delUsuario = crearCarrito();
        UUID uno = producto(tienda, "10.00", 500);
        UUID dos = producto(tienda, "5.00", 500);
        agregar(delUsuario, uno, 98).andExpect(status().isOk());
        vincular(delUsuario, sesion(usuario)).andExpect(status().isOk());

        String invitado = crearCarrito();
        agregar(invitado, uno, 2).andExpect(status().isOk());
        agregar(invitado, dos, 1).andExpect(status().isOk());

        JsonNode resultado = cuerpo(vincular(invitado, sesion(usuario)).andExpect(status().isOk())).get("datos");

        assertThat(resultado.get("token").asText()).isEqualTo(delUsuario);
        Map<String, Integer> cantidades = new java.util.HashMap<>();
        resultado.get("items").forEach(i -> cantidades.put(i.get("variante_id").asText(), i.get("cantidad").asInt()));
        assertThat(cantidades.get(uno.toString())).isEqualTo(99);
        assertThat(cantidades.get(dos.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Carritos\" WHERE \"TokenSesion\" = ?", String.class, invitado))
                .isEqualTo("CONVERTIDO");

        agregar(invitado, uno, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAR-008"));
    }
}
