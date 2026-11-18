package shopify.api.core.modulos.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-020: las tarjetas del mercado y de la vitrina traen descripción corta y un resumen de variantes. */
class VariantesVisiblesTest extends PruebaIntegracionBase {

    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        tienda = crearTiendaOperativa(crearUsuarioActivo(false));
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    private UUID producto(String titulo, String descripcion, String... variantes) {
        UUID id = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", \"Descripcion\", \"EstaPublicado\") "
                + "VALUES (?, ?, ?, ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, titulo, "p-" + sufijo(), descripcion);
        for (String variante : variantes) {
            UUID v = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", \"Precio\") "
                    + "VALUES (?, ?, ?, 20) RETURNING \"Id\"", UUID.class, id, variante, "SKU-" + sufijo());
            jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                    + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 5, 0, TRUE, FALSE)", v);
        }
        jdbc.update("INSERT INTO \"ImagenesProducto\" (\"ProductoId\", \"UrlImagen\", \"EsPrincipal\") VALUES (?, 'https://cdn.prueba.test/x.png', TRUE)", id);
        return id;
    }

    private JsonNode tarjeta(String ruta, String titulo) throws Exception {
        for (JsonNode t : cuerpo(getConToken(ruta, null, null).andExpect(status().isOk())).get("datos").get("datos")) {
            if (t.get("titulo").asText().equals(titulo)) {
                return t;
            }
        }
        throw new AssertionError("No aparece " + titulo);
    }

    @Test
    void resumenDeVariantesYDescripcionCortaEnMercadoYVitrina() throws Exception {
        String larga = "<p>Una prenda <b>muy</b> cómoda.</p>   " + "palabra ".repeat(40);
        producto("Con opciones", larga, "Negro / S", "Negro / M", "Negro / L", "Rojo / S", "Rojo / M");
        producto("Sin opciones", null, "Por defecto");
        producto("Una opción", "Descripción breve.", "Azul");

        for (String ruta : new String[]{"/mercado/productos?tienda=" + subdominio, "/vitrina/" + subdominio + "/productos"}) {
            JsonNode con = tarjeta(ruta, "Con opciones");
            assertThat(con.get("cantidad_variantes").asInt()).isEqualTo(5);
            assertThat(con.get("variantes_resumen")).hasSize(3);
            assertThat(con.get("variantes_resumen").get(0).get("titulo").asText()).isEqualTo("Negro / S");
            assertThat(con.get("variantes_resumen").get(0).get("precio").decimalValue()).isEqualByComparingTo("20");
            String corta = con.get("descripcion_corta").asText();
            assertThat(corta.length()).isLessThanOrEqualTo(110);
            assertThat(corta).startsWith("Una prenda muy cómoda.").doesNotContain("<").endsWith("…");

            JsonNode sin = tarjeta(ruta, "Sin opciones");
            assertThat(sin.get("cantidad_variantes").asInt()).isEqualTo(1);
            assertThat(sin.get("variantes_resumen")).isEmpty();
            assertThat(sin.has("descripcion_corta")).isFalse();

            JsonNode una = tarjeta(ruta, "Una opción");
            assertThat(una.get("cantidad_variantes").asInt()).isEqualTo(1);
            assertThat(una.get("variantes_resumen")).hasSize(1);
            assertThat(una.get("descripcion_corta").asText()).isEqualTo("Descripción breve.");
        }
    }
}
