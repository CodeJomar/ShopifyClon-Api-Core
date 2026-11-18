package shopify.api.core.modulos.resena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-011: reseñas y calificaciones de productos. */
class ResenaTest extends PruebaIntegracionBase {

    private UUID duenio;
    private UUID tienda;
    private String subdominio;
    private UUID producto;
    private UUID variante;
    private String rutaResenas;

    @BeforeEach
    void preparar() {
        duenio = crearUsuarioActivo(false);
        tienda = crearTiendaOperativa(duenio);
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
        String slug = "prod-" + sufijo();
        producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", \"EstaPublicado\") "
                + "VALUES (?, 'Mochila', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, slug);
        variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                + "VALUES (?, 'Por defecto', ?, 20) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 50, 0, TRUE, FALSE)", variante);
        jdbc.update("INSERT INTO \"ImagenesProducto\" (\"ProductoId\", \"UrlImagen\", \"EsPrincipal\") VALUES (?, 'https://cdn.prueba.test/m.png', TRUE)",
                producto);
        rutaResenas = "/vitrina/" + subdominio + "/productos/" + slug + "/resenas";
    }

    private String jwt(UUID id) {
        return tokenPara(id, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id));
    }

    /** Comprador con una orden pagada de este producto (sembrada directamente en la base). */
    private UUID compradorConCompra(String estadoPago) {
        UUID comprador = crearUsuarioActivo(false);
        UUID orden = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"UsuarioId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\", \"EstadoPago\") VALUES (?, ?, ?, 'c@prueba.test', 'Cliente', 20, 20, ?) "
                + "RETURNING \"Id\"", UUID.class, tienda, comprador, "#" + sufijo(), estadoPago);
        jdbc.update("INSERT INTO \"ItemsOrden\" (\"OrdenId\", \"VarianteId\", \"TituloProducto\", \"TituloVariante\", \"Cantidad\", "
                + "\"PrecioUnitario\", \"TotalLinea\") VALUES (?, ?, 'Mochila', 'Por defecto', 1, 20, 20)", orden, variante);
        return comprador;
    }

    private ResultActions enviar(MockHttpServletRequestBuilder builder, String jwt, Object cuerpo) throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON);
        if (jwt != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        }
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(builder);
    }

    private JsonNode resenar(UUID persona, int calificacion, String comentario) throws Exception {
        return cuerpo(enviar(post(rutaResenas), jwt(persona), Map.of("calificacion", calificacion, "comentario", comentario))
                .andExpect(status().isCreated())).get("datos");
    }

    private JsonNode leer(String jwt, String query) throws Exception {
        return cuerpo(getConToken(rutaResenas + query, jwt, null).andExpect(status().isOk())).get("datos");
    }

    @Test
    void sinCompraPagadaNoSePuedeReseñar() throws Exception {
        UUID sinCompra = crearUsuarioActivo(false);
        enviar(post(rutaResenas), jwt(sinCompra), Map.of("calificacion", 5)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("RES-001"));
        assertThat(leer(jwt(sinCompra), "").get("puedo_resenar").asBoolean()).isFalse();

        UUID pendiente = compradorConCompra("PENDIENTE");
        enviar(post(rutaResenas), jwt(pendiente), Map.of("calificacion", 5)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("RES-001"));
        enviar(post(rutaResenas), null, Map.of("calificacion", 5)).andExpect(status().isUnauthorized());
    }

    @Test
    void conCompraPagadaSePublicaYAparecenResumenYAutor() throws Exception {
        UUID comprador = compradorConCompra("PAGADO");
        assertThat(leer(jwt(comprador), "").get("puedo_resenar").asBoolean()).isTrue();

        JsonNode creada = resenar(comprador, 5, "Muy buena");
        assertThat(creada.get("calificacion").asInt()).isEqualTo(5);
        assertThat(creada.get("compra_verificada").asBoolean()).isTrue();
        assertThat(creada.toString()).doesNotContain("@");

        UUID otro = compradorConCompra("PARCIALMENTE_REEMBOLSADO");
        resenar(otro, 4, "Buena");

        JsonNode publico = leer(null, "");
        assertThat(publico.get("resumen").get("promedio").decimalValue()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(publico.get("resumen").get("total").asInt()).isEqualTo(2);
        assertThat(publico.get("resumen").get("distribucion").get("5").asInt()).isEqualTo(1);
        assertThat(publico.get("resumen").get("distribucion").get("4").asInt()).isEqualTo(1);
        assertThat(publico.get("resumen").get("distribucion").get("1").asInt()).isZero();
        assertThat(publico.has("mi_resena")).isFalse();
        assertThat(publico.get("puedo_resenar").asBoolean()).isFalse();
        assertThat(publico.get("pagina").get("datos")).hasSize(2);
        assertThat(publico.get("pagina").get("total_paginas").asInt()).isEqualTo(1);

        JsonNode mia = leer(jwt(comprador), "?orden=mejores");
        assertThat(mia.get("mi_resena").get("calificacion").asInt()).isEqualTo(5);
        assertThat(mia.get("pagina").get("datos").get(0).get("calificacion").asInt()).isEqualTo(5);
        getConToken(rutaResenas + "?orden=raro", null, null).andExpect(status().isBadRequest());
    }

    @Test
    void duplicadaEditarYEliminar() throws Exception {
        UUID comprador = compradorConCompra("PAGADO");
        UUID id = UUID.fromString(resenar(comprador, 3, "Regular").get("id").asText());
        enviar(post(rutaResenas), jwt(comprador), Map.of("calificacion", 5)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RES-003"));

        enviar(put("/cuenta/resenas/" + id), jwt(comprador), Map.of("calificacion", 5, "comentario", "Mejoró"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.calificacion").value(5));

        // Otra persona no puede editarla ni borrarla: para ella no existe
        UUID otro = compradorConCompra("PAGADO");
        enviar(put("/cuenta/resenas/" + id), jwt(otro), Map.of("calificacion", 1)).andExpect(status().isNotFound());
        enviar(MockMvcRequestBuilders.delete("/cuenta/resenas/" + id), jwt(otro), null).andExpect(status().isNotFound());

        enviar(MockMvcRequestBuilders.delete("/cuenta/resenas/" + id), jwt(comprador), null).andExpect(status().isOk());
        assertThat(leer(null, "").get("resumen").get("total").asInt()).isZero();
        resenar(comprador, 4, "Otra vez");
    }

    @Test
    void losMiembrosDeLaTiendaNoReseñanSusProductos() throws Exception {
        // El propietario compra su propio producto (orden pagada) pero no puede reseñarlo
        UUID orden = jdbc.queryForObject("INSERT INTO \"Ordenes\" (\"TiendaId\", \"UsuarioId\", \"NumeroOrden\", \"CorreoCliente\", "
                + "\"NombreCliente\", \"Subtotal\", \"Total\", \"EstadoPago\") VALUES (?, ?, ?, 'd@prueba.test', 'Dueno', 20, 20, 'PAGADO') "
                + "RETURNING \"Id\"", UUID.class, tienda, duenio, "#" + sufijo());
        jdbc.update("INSERT INTO \"ItemsOrden\" (\"OrdenId\", \"VarianteId\", \"TituloProducto\", \"TituloVariante\", \"Cantidad\", "
                + "\"PrecioUnitario\", \"TotalLinea\") VALUES (?, ?, 'Mochila', 'Por defecto', 1, 20, 20)", orden, variante);

        enviar(post(rutaResenas), jwt(duenio), Map.of("calificacion", 5)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("RES-002"));
        assertThat(leer(jwt(duenio), "").get("puedo_resenar").asBoolean()).isFalse();
    }

    @Test
    void validacionesYProductoInexistente() throws Exception {
        UUID comprador = compradorConCompra("PAGADO");
        enviar(post(rutaResenas), jwt(comprador), Map.of("calificacion", 0)).andExpect(status().isUnprocessableEntity());
        enviar(post(rutaResenas), jwt(comprador), Map.of("calificacion", 6)).andExpect(status().isUnprocessableEntity());
        enviar(post(rutaResenas), jwt(comprador), Map.of("calificacion", 5, "comentario", "x".repeat(1001)))
                .andExpect(status().isUnprocessableEntity());
        getConToken("/vitrina/" + subdominio + "/productos/no-existe/resenas", null, null)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("CAT-404"));
    }

    @Test
    void laModeracionOcultaYDejaDeContarEnElPromedio() throws Exception {
        UUID uno = compradorConCompra("PAGADO");
        UUID dos = compradorConCompra("PAGADO");
        UUID idUno = UUID.fromString(resenar(uno, 1, "Malo").get("id").asText());
        resenar(dos, 5, "Excelente");
        UUID admin = crearUsuarioActivo(true);

        enviar(post("/plataforma/resenas/" + idUno + "/ocultar"), jwt(uno), Map.of("motivo", "Lenguaje ofensivo"))
                .andExpect(status().isNotFound());
        enviar(post("/plataforma/resenas/" + idUno + "/ocultar"), jwt(admin), Map.of("motivo", "Lenguaje ofensivo"))
                .andExpect(status().isOk());

        JsonNode resumen = leer(null, "").get("resumen");
        assertThat(resumen.get("total").asInt()).isEqualTo(1);
        assertThat(resumen.get("promedio").decimalValue()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(leer(jwt(uno), "").has("mi_resena")).isFalse();
        enviar(put("/cuenta/resenas/" + idUno), jwt(uno), Map.of("calificacion", 5)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RES-005"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'RESENA_OCULTADA' "
                + "AND \"EntidadId\" = ?", Integer.class, idUno)).isEqualTo(1);
    }

    @Test
    void elPromedioSaleEnElMercadoYEnLaVitrina() throws Exception {
        resenar(compradorConCompra("PAGADO"), 5, "Genial");
        resenar(compradorConCompra("PAGADO"), 4, "Bien");

        JsonNode mercado = cuerpo(getConToken("/mercado/productos?tienda=" + subdominio + "&orden=mejor_calificados", null, null)
                .andExpect(status().isOk())).get("datos").get("datos").get(0);
        assertThat(mercado.get("calificacion_promedio").decimalValue()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(mercado.get("cantidad_resenas").asInt()).isEqualTo(2);

        JsonNode vitrina = cuerpo(getConToken("/vitrina/" + subdominio + "/productos?orden=mejor_calificados", null, null)
                .andExpect(status().isOk())).get("datos").get("datos").get(0);
        assertThat(vitrina.get("calificacion_promedio").decimalValue()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(vitrina.get("cantidad_resenas").asInt()).isEqualTo(2);

        String slug = rutaResenas.substring(rutaResenas.indexOf("/productos/") + 11, rutaResenas.lastIndexOf("/resenas"));
        JsonNode detalle = cuerpo(getConToken("/vitrina/" + subdominio + "/productos/" + slug, null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(detalle.get("calificacion_promedio").decimalValue()).isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(detalle.get("cantidad_resenas").asInt()).isEqualTo(2);
    }

    @Test
    void unProductoSinReseñasNoTienePromedio() throws Exception {
        JsonNode mercado = cuerpo(getConToken("/mercado/productos?tienda=" + subdominio, null, null)
                .andExpect(status().isOk())).get("datos").get("datos").get(0);
        assertThat(mercado.has("calificacion_promedio")).isFalse();
        assertThat(mercado.get("cantidad_resenas").asInt()).isZero();
    }
}
