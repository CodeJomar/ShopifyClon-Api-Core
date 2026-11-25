package shopify.api.core.modulos.semilla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.semilla.servicio.SemillaDemoServicio;
import shopify.api.core.modulos.semilla.servicio.SemillaDemoServicio.Resumen;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** R-014: la semilla de demostración es completa, idempotente y deja el mercado y la administración con datos. */
class SemillaDemoTest extends PruebaIntegracionBase {

    private static final String CLAVE = "Demo-Segura-2026!";
    private static final List<String> SUBDOMINIOS = List.of("cafe-andino", "casa-quinua", "taller-textil-cusco", "neo-gadgets",
            "aroma-selva", "papel-y-tinta", "dulce-pacha", "patitas-andinas", "altura-outdoor", "hogar-quillay", "libros-del-sur", "joyas-inti");

    private static final String SUBDOMINIOS_SQL = SUBDOMINIOS.stream().map(x -> "'" + x + "'").collect(java.util.stream.Collectors.joining(","));

    @Autowired
    private SemillaDemoServicio semilla;

    private long contar(String sql) {
        Long total = jdbc.queryForObject(sql, Long.class);
        return total == null ? 0 : total;
    }

    private Map<String, Long> conteos() {
        String enTiendas = "(SELECT \"Id\" FROM \"Tiendas\" WHERE \"Subdominio\" IN (%s))".formatted(SUBDOMINIOS_SQL);
        return Map.of(
                "cuentas", contar("SELECT count(*) FROM \"Usuarios\" WHERE \"CorreoElectronico\" LIKE '%.demo@otemu.test'"),
                "tiendas", contar("SELECT count(*) FROM " + enTiendas + " t"),
                "productos", contar("SELECT count(*) FROM \"Productos\" WHERE \"TiendaId\" IN " + enTiendas),
                "variantes", contar("SELECT count(*) FROM \"VariantesProducto\" v JOIN \"Productos\" p ON p.\"Id\" = v.\"ProductoId\" "
                        + "WHERE p.\"TiendaId\" IN " + enTiendas),
                "tarifas", contar("SELECT count(*) FROM \"TarifasEnvio\" WHERE \"TiendaId\" IN " + enTiendas),
                "ordenes", contar("SELECT count(*) FROM \"Ordenes\" WHERE \"TiendaId\" IN " + enTiendas),
                "pedidos", contar("SELECT count(*) FROM \"Pedidos\" WHERE \"CorreoCliente\" LIKE '%.demo@otemu.test'"),
                "resenas", contar("SELECT count(*) FROM \"ResenasProducto\" WHERE \"TiendaId\" IN " + enTiendas));
    }

    @Test
    void creaTodoYUnaSegundaCorridaNoDuplicaNada() {
        Resumen primera = semilla.ejecutar(CLAVE, "admin.demo@otemu.test");
        Map<String, Long> tras1 = conteos();

        assertThat(tras1.get("cuentas")).isEqualTo(7);
        assertThat(tras1.get("tiendas")).isEqualTo(12);
        assertThat(tras1.get("productos")).isEqualTo(120);
        assertThat(tras1.get("tarifas")).isEqualTo(24);
        assertThat(tras1.get("pedidos")).isGreaterThanOrEqualTo(5);
        assertThat(tras1.get("resenas")).isEqualTo(108);
        // La base se comparte entre las pruebas: la primera corrida de esta puede ya encontrar todo creado
        assertThat(primera.creados().getOrDefault("tiendas", 0) + primera.omitidos().getOrDefault("tiendas", 0)).isEqualTo(12);

        Resumen segunda = semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        assertThat(conteos()).isEqualTo(tras1);
        assertThat(segunda.creados()).isEmpty();
    }

    @Test
    void elRepartoDeTiendasEsPropietarioTresAdminUnaYNadieReseniaLaSuya() {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        String sql = "SELECT count(*) FROM \"Tiendas\" t JOIN \"Usuarios\" u ON u.\"Id\" = t.\"PropietarioId\" WHERE u.\"CorreoElectronico\" = ? AND t.\"Subdominio\" IN (" + SUBDOMINIOS_SQL + ")";
        assertThat(jdbc.queryForObject(sql, Long.class, "propietario.demo@otemu.test")).isEqualTo(3);
        assertThat(jdbc.queryForObject(sql, Long.class, "admin.demo@otemu.test")).isEqualTo(1);
        assertThat(jdbc.queryForObject(sql, Long.class, "propietario2.demo@otemu.test")).isEqualTo(2);
        for (String n : List.of("3", "4", "5")) {
            assertThat(jdbc.queryForObject(sql, Long.class, "propietario" + n + ".demo@otemu.test")).isEqualTo(2);
        }
        // Cada tienda tiene 10 productos, uno con poco stock y otro agotado
        String stock = "SELECT count(*) FROM \"Tiendas\" t WHERE t.\"Subdominio\" IN (" + SUBDOMINIOS_SQL + ") AND EXISTS (SELECT 1 FROM \"Productos\" p "
                + "JOIN \"VariantesProducto\" v ON v.\"ProductoId\" = p.\"Id\" JOIN \"InventarioVariante\" i ON i.\"VarianteId\" = v.\"Id\" "
                + "WHERE p.\"TiendaId\" = t.\"Id\" AND p.\"EsDigital\" = FALSE AND i.\"CantidadDisponible\" %s)";
        assertThat(jdbc.queryForObject(stock.formatted("BETWEEN 1 AND 5"), Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject(stock.formatted("= 0"), Long.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"ResenasProducto\" r JOIN \"Tiendas\" t ON t.\"Id\" = r.\"TiendaId\" "
                + "WHERE r.\"UsuarioId\" = t.\"PropietarioId\" AND t.\"Subdominio\" IN (" + SUBDOMINIOS_SQL + ")", Long.class)).isZero();
    }

    @Test
    void lasFotosRealesDelCatalogoSeGuardanYSeSirvenSinSesion() throws Exception {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        String archivo = jdbc.queryForObject("SELECT substring(\"UrlImagen\" from '[^/]+$') FROM \"ImagenesProducto\" "
                + "WHERE \"UrlImagen\" LIKE '%/semilla-fotos/%' LIMIT 1", String.class);
        assertThat(archivo).endsWith(".jpg");
        assertThat(contar("SELECT count(*) FROM \"ImagenesProducto\" WHERE \"UrlImagen\" LIKE '%/semilla-fotos/%'")).isGreaterThanOrEqualTo(200);
        getConToken("/semilla-fotos/" + archivo, null, null).andExpect(status().isOk());
    }

    @Test
    void elMercadoMuestraProductosTiendasYEstrellas() throws Exception {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        for (String subdominio : SUBDOMINIOS) {
            JsonNode productos = cuerpo(getConToken("/mercado/productos?tienda=" + subdominio, null, null).andExpect(status().isOk()))
                    .get("datos");
            assertThat(productos.get("total").asInt()).isEqualTo(10);
            getConToken("/mercado/tiendas/" + subdominio, null, null).andExpect(status().isOk());
        }
        JsonNode conEstrellas = cuerpo(getConToken("/mercado/productos?tienda=cafe-andino&orden=mejor_calificados", null, null)
                .andExpect(status().isOk())).get("datos").get("datos").get(0);
        assertThat(conEstrellas.get("calificacion_promedio").decimalValue().doubleValue()).isBetween(3.0, 5.0);
        assertThat(conEstrellas.get("cantidad_resenas").asInt()).isEqualTo(3);
    }

    @Test
    void lasVariantesYLasFotosDeEjemploSeVenEnLaTarjetaYElDetalle() throws Exception {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        JsonNode tarjetas = cuerpo(getConToken("/mercado/productos?tienda=taller-textil-cusco", null, null).andExpect(status().isOk()))
                .get("datos").get("datos");
        JsonNode polo = null;
        for (JsonNode t : tarjetas) {
            if (t.get("identificador_url").asText().equals("polo-de-algodon-pima")) {
                polo = t;
            }
        }
        assertThat(polo).isNotNull();
        assertThat(polo.get("cantidad_variantes").asInt()).isEqualTo(12);
        assertThat(polo.get("variantes_resumen")).hasSize(3);
        assertThat(polo.get("variantes_resumen").get(0).get("titulo").asText()).isEqualTo("Blanco / S");
        assertThat(polo.get("descripcion_corta").asText().length()).isLessThanOrEqualTo(110);

        JsonNode detalle = cuerpo(getConToken("/vitrina/taller-textil-cusco/productos/polo-de-algodon-pima", null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(detalle.get("variantes")).hasSize(12);
        assertThat(detalle.get("imagenes")).hasSize(3);
        long asociadas = 0;
        for (JsonNode imagen : detalle.get("imagenes")) {
            asociadas += imagen.has("variante_id") ? 1 : 0;
        }
        assertThat(asociadas).isEqualTo(2);

        JsonNode taza = cuerpo(getConToken("/vitrina/cafe-andino/productos/taza-de-ceramica-artesanal", null, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(taza.get("variantes")).hasSize(3);
        assertThat(taza.get("imagenes")).hasSize(3);

        JsonNode buds = cuerpo(getConToken("/vitrina/neo-gadgets/productos/audifonos-inalambricos-neo-buds", null, null)
                .andExpect(status().isOk())).get("datos");
        boolean agotada = false;
        boolean conComparacion = false;
        for (JsonNode v : buds.get("variantes")) {
            agotada |= !v.get("disponible").asBoolean();
            conComparacion |= v.has("precio_comparacion");
        }
        assertThat(agotada).isTrue();
        assertThat(conComparacion).isTrue();
    }

    @Test
    void elAdministradorDemoEntraYVeLasDoceTiendas() throws Exception {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        String token = cuerpo(postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", "admin.demo@otemu.test", "contrasena", CLAVE), null, null)
                .andExpect(status().isOk())).get("datos").get("token").asText();

        for (String subdominio : SUBDOMINIOS) {
            JsonNode tiendas = cuerpo(getConToken("/plataforma/tiendas?q=" + subdominio, token, null).andExpect(status().isOk())).get("datos");
            assertThat(tiendas.get("total").asInt()).isEqualTo(1);
            assertThat(tiendas.get("datos").get(0).get("moneda").asText()).isEqualTo("PEN");
        }
        JsonNode perfil = cuerpo(getConToken(Rutas.CUENTA_PERFIL, token, null).andExpect(status().isOk())).get("datos");
        assertThat(perfil.get("acceso").get("rol_plataforma").get("codigo").asText()).isEqualTo("ADMIN_PLATAFORMA");
    }

    @Test
    void elCompradorTienePedidosEnVariosEstadosYElPropietarioVeVentas() throws Exception {
        semilla.ejecutar(CLAVE, "admin.demo@otemu.test");

        String comprador = cuerpo(postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", "comprador.demo@otemu.test", "contrasena", CLAVE), null, null)
                .andExpect(status().isOk())).get("datos").get("token").asText();
        JsonNode pedidos = cuerpo(getConToken("/cuenta/pedidos", comprador, null).andExpect(status().isOk())).get("datos");
        assertThat(pedidos.get("total").asInt()).isGreaterThanOrEqualTo(10);
        String estados = pedidos.toString();
        assertThat(estados).contains("PARCIALMENTE_PAGADO", "ENTREGADO", "DESPACHADO", "PAGADO");

        String propietario = cuerpo(postJson(Rutas.AUTH_LOGIN, Map.of("correo_electronico", "propietario.demo@otemu.test", "contrasena", CLAVE), null, null)
                .andExpect(status().isOk())).get("datos").get("token").asText();
        JsonNode resumen = cuerpo(getConToken("/propietario/resumen", propietario, null).andExpect(status().isOk())).get("datos");
        assertThat(resumen.get("tiendas")).hasSize(3);
        assertThat(resumen.get("totales").get(0).get("ventas").decimalValue().signum()).isPositive();
    }

    @Test
    void sinContrasenaCortaNoCorre() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> semilla.ejecutar("corta", "admin.demo@otemu.test"));
    }
}
