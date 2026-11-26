package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.modulos.tienda.modelo.OpcionesApariencia;
import shopify.api.core.soporte.Rutas;

/** R-031: apariencia de la vitrina (cinco códigos de lista blanca y logo por URL https). */
class AparienciaTiendaTest extends PruebaTiendaBase {

    private static final String PANEL = "/panel/tienda/apariencia";

    private Map<String, Object> apariencia(String paleta, String tipografia, String tamano, String diseno, String modo) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("paleta", paleta);
        cuerpo.put("tipografia", tipografia);
        cuerpo.put("tamano_letra", tamano);
        cuerpo.put("diseno", diseno);
        cuerpo.put("modo", modo);
        return cuerpo;
    }

    private Map<String, Object> distinta() {
        return apariencia("BOSQUE", "CLASICA", "GRANDE", "REVISTA", "CLARO");
    }

    /** Envía JSON tal cual, para distinguir un campo ausente de un {@code null} explícito. */
    private ResultActions putCrudo(String json, String jwt, UUID tienda) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.put(PANEL).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header("X-Tienda-Id", tienda.toString()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String logoPropio(UUID tienda) {
        return "https://api.ejemplo.test/api/v1/archivos/" + tienda + "/" + UUID.randomUUID() + ".png";
    }

    private String subdominioDe(UUID tienda) {
        return jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    private void publicarUnProducto(UUID tienda) {
        jdbc.update("UPDATE \"Tiendas\" SET \"VisibleEnMercado\" = TRUE, \"EstaActiva\" = TRUE WHERE \"Id\" = ?", tienda);
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\", \"RequiereEnvio\") VALUES (?, 'Por defecto', ?, 10, FALSE) RETURNING \"Id\"", UUID.class, producto,
                "SKU-" + sufijo());
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, 5, 0, TRUE, FALSE)", variante);
        jdbc.update("INSERT INTO \"ImagenesProducto\" (\"ProductoId\", \"UrlImagen\", \"EsPrincipal\", \"Posicion\") "
                + "VALUES (?, 'https://ejemplo.test/foto.jpg', TRUE, 1)", producto);
    }

    @Test
    void lasOpcionesSonPublicasYCoincidenConLosPatronesDeValidacion() throws Exception {
        JsonNode datos = cuerpo(getConToken("/apariencia/opciones", null, null).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("opciones").get("paleta")).hasSize(6);
        assertThat(datos.get("opciones").get("tipografia")).hasSize(4);
        assertThat(datos.get("opciones").get("tamano_letra")).hasSize(3);
        assertThat(datos.get("opciones").get("diseno")).hasSize(4);
        assertThat(datos.get("opciones").get("modo")).hasSize(3);
        JsonNode porDefecto = datos.get("por_defecto");
        assertThat(porDefecto.get("paleta").asText()).isEqualTo("OTEMU");
        assertThat(porDefecto.get("tipografia").asText()).isEqualTo("MODERNA");
        assertThat(porDefecto.get("tamano_letra").asText()).isEqualTo("NORMAL");
        assertThat(porDefecto.get("diseno").asText()).isEqualTo("CUADRICULA");
        assertThat(porDefecto.get("modo").asText()).isEqualTo("OSCURO");

        // Las listas y los patrones de validación no pueden desviarse
        for (var par : Map.of(OpcionesApariencia.PALETA, OpcionesApariencia.PATRON_PALETA,
                OpcionesApariencia.TIPOGRAFIA, OpcionesApariencia.PATRON_TIPOGRAFIA,
                OpcionesApariencia.TAMANO_LETRA, OpcionesApariencia.PATRON_TAMANO_LETRA,
                OpcionesApariencia.DISENO, OpcionesApariencia.PATRON_DISENO,
                OpcionesApariencia.MODO, OpcionesApariencia.PATRON_MODO).entrySet()) {
            Pattern patron = Pattern.compile(par.getValue());
            par.getKey().forEach(codigo -> assertThat(patron.matcher(codigo).matches()).as(codigo).isTrue());
            assertThat(patron.matcher("INVENTADO").matches()).isFalse();
        }
    }

    @Test
    void unaTiendaNuevaTraeLosValoresPorDefectoEnElPanelYEnLoPublico() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        publicarUnProducto(tienda);

        for (String ruta : List.of(Rutas.PANEL_TIENDA)) {
            JsonNode a = cuerpo(getConToken(ruta, propietario.token(), tienda).andExpect(status().isOk())).get("datos").get("apariencia");
            assertThat(a.get("paleta").asText()).isEqualTo("OTEMU");
            assertThat(a.get("modo").asText()).isEqualTo("OSCURO");
            assertThat(a.has("logo_url")).isFalse();
        }
        JsonNode vitrina = cuerpo(getConToken("/vitrina/" + subdominioDe(tienda), null, null).andExpect(status().isOk())).get("datos");
        assertThat(vitrina.get("apariencia").get("tipografia").asText()).isEqualTo("MODERNA");
        JsonNode mercado = cuerpo(getConToken("/mercado/tiendas/" + subdominioDe(tienda), null, null).andExpect(status().isOk())).get("datos");
        assertThat(mercado.get("apariencia").get("diseno").asText()).isEqualTo("CUADRICULA");
    }

    @Test
    void elPropietarioCambiaLaAparienciaYSeVeAlInstanteEnLoPublicoYQuedaAuditada() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        publicarUnProducto(tienda);
        String sub = subdominioDe(tienda);
        // Se lee antes para que la lectura recordada tenga la apariencia vieja
        getConToken("/vitrina/" + sub, null, null).andExpect(status().isOk());
        getConToken("/mercado/tiendas/" + sub, null, null).andExpect(status().isOk());

        putJson(PANEL, distinta(), propietario.token(), tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.paleta").value("BOSQUE"))
                .andExpect(jsonPath("$.datos.tamano_letra").value("GRANDE"))
                .andExpect(jsonPath("$.datos.modo").value("CLARO"));

        assertThat(cuerpo(getConToken("/vitrina/" + sub, null, null)).get("datos").get("apariencia").get("diseno").asText()).isEqualTo("REVISTA");
        assertThat(cuerpo(getConToken("/mercado/tiendas/" + sub, null, null)).get("datos").get("apariencia").get("paleta").asText()).isEqualTo("BOSQUE");
        assertThat(cuerpo(getConToken(Rutas.PANEL_TIENDA, propietario.token(), tienda)).get("datos").get("apariencia").get("tipografia").asText())
                .isEqualTo("CLASICA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'TIENDA_APARIENCIA_CAMBIADA' "
                + "AND \"EntidadId\" = ?", Integer.class, tienda)).isEqualTo(1);

        // Repetir lo mismo no vuelve a auditar
        putJson(PANEL, distinta(), propietario.token(), tienda).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'TIENDA_APARIENCIA_CAMBIADA' "
                + "AND \"EntidadId\" = ?", Integer.class, tienda)).isEqualTo(1);
    }

    @Test
    void losCodigosFaltantesOFueraDeLaListaBlancaSeRechazanPorCampo() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());

        putJson(PANEL, Map.of("paleta", "OTEMU"), propietario.token(), tienda).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.tipografia").exists())
                .andExpect(jsonPath("$.validaciones.tamano_letra").exists())
                .andExpect(jsonPath("$.validaciones.diseno").exists())
                .andExpect(jsonPath("$.validaciones.modo").exists());
        for (String campo : List.of("paleta", "tipografia", "tamano_letra", "diseno", "modo")) {
            for (String malo : List.of("INVENTADO", "otemu", "OTEMU ", "")) {
                Map<String, Object> cuerpo = new LinkedHashMap<>(distinta());
                cuerpo.put(campo, malo);
                putJson(PANEL, cuerpo, propietario.token(), tienda).andExpect(status().isUnprocessableEntity())
                        .andExpect(jsonPath("$.validaciones." + campo).exists());
            }
        }
        // Nada de lo rechazado cambió la tienda
        assertThat(jdbc.queryForObject("SELECT \"Paleta\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda)).isEqualTo("OTEMU");
    }

    @Test
    void soloQuienPuedeConfigurarLaTiendaCambiaLaApariencia() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        Sesion pedidos = personalConRol(tienda, "GESTOR_PEDIDOS");
        Sesion administrador = personalConRol(tienda, "ADMINISTRADOR");

        putJson(PANEL, distinta(), null, tienda).andExpect(status().isUnauthorized());
        putJson(PANEL, distinta(), pedidos.token(), tienda).andExpect(status().isForbidden());
        putJson(PANEL, distinta(), nuevaSesion().token(), tienda).andExpect(status().is4xxClientError());
        putJson(PANEL, distinta(), administrador.token(), tienda).andExpect(status().isOk());
    }

    @Test
    void elLogoAceptaUnaUrlHttpsPropiaOExternaYRechazaElResto() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());

        for (String malo : List.of("http://cdn.ejemplo.test/logo.png", "javascript:alert(1)", "data:image/png;base64,AAAA",
                "ftp://cdn.ejemplo.test/logo.png", "//cdn.ejemplo.test/logo.png", "https://", "https:///logo.png",
                "https://cdn.ejemplo.test/mi logo.png", "https://cdn.ejemplo.test/mi\tlogo.png", "https://usuario:clave@cdn.ejemplo.test/logo.png",
                "https://cdn.ejemplo.test/\"onerror=alert(1)", "https://cdn.ejemplo.test/<script>", "logo.png",
                "https://cdn.ejemplo.test/" + "a".repeat(500))) {
            Map<String, Object> cuerpo = new LinkedHashMap<>(distinta());
            cuerpo.put("logo_url", malo);
            ResultActions respuesta = putJson(PANEL, cuerpo, propietario.token(), tienda);
            assertThat(respuesta.andReturn().getResponse().getStatus()).as("logo rechazado: " + malo).isEqualTo(422);
            respuesta.andExpect(jsonPath("$.codigo").value("VAL-001")).andExpect(jsonPath("$.validaciones.logo_url").exists());
        }
        assertThat(jdbc.queryForObject("SELECT \"UrlLogo\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda)).isNull();

        // Los espacios y saltos de línea de los extremos (al pegar la URL) se recortan antes de validar
        Map<String, Object> conBordes = new LinkedHashMap<>(distinta());
        conBordes.put("logo_url", "  https://cdn.ejemplo.test/recortado.png\n");
        putJson(PANEL, conBordes, propietario.token(), tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.logo_url").value("https://cdn.ejemplo.test/recortado.png"));

        // Una URL externa https, una subida a la API y una de exactamente 500 caracteres se aceptan
        String externa = "https://cdn.ejemplo.test/mi-logo.png?v=2";
        Map<String, Object> conExterna = new LinkedHashMap<>(distinta());
        conExterna.put("logo_url", externa);
        putJson(PANEL, conExterna, propietario.token(), tienda).andExpect(status().isOk()).andExpect(jsonPath("$.datos.logo_url").value(externa));
        String limite = "https://cdn.ejemplo.test/" + "a".repeat(500 - "https://cdn.ejemplo.test/".length());
        assertThat(limite).hasSize(500);
        conExterna.put("logo_url", limite);
        putJson(PANEL, conExterna, propietario.token(), tienda).andExpect(status().isOk());

        // Uno subido a la API y devuelto por POST /panel/archivos/imagenes también
        String logo = logoPropio(tienda);
        Map<String, Object> conLogo = new LinkedHashMap<>(distinta());
        conLogo.put("logo_url", logo);
        putJson(PANEL, conLogo, propietario.token(), tienda).andExpect(status().isOk()).andExpect(jsonPath("$.datos.logo_url").value(logo));
        publicarUnProducto(tienda);
        assertThat(cuerpo(getConToken("/mercado/tiendas/" + subdominioDe(tienda), null, null)).get("datos").get("apariencia")
                .get("logo_url").asText()).isEqualTo(logo);

        // Ausente conserva el logo; null explícito lo quita
        putJson(PANEL, apariencia("ROSA", "COMPACTA", "MUY_GRANDE", "LISTA", "ALTO_CONTRASTE"), propietario.token(), tienda)
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.logo_url").value(logo));
        putCrudo("{\"paleta\":\"ROSA\",\"tipografia\":\"COMPACTA\",\"tamano_letra\":\"MUY_GRANDE\",\"diseno\":\"LISTA\","
                + "\"modo\":\"ALTO_CONTRASTE\",\"logo_url\":null}", propietario.token(), tienda)
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.logo_url").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT \"UrlLogo\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda)).isNull();
    }

    @Test
    void laBaseRechazaCodigosFueraDeLaListaBlanca() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"Tiendas\" SET \"Paleta\" = 'FUCSIA' WHERE \"Id\" = ?", tienda));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"Tiendas\" SET \"Modo\" = 'NEON' WHERE \"Id\" = ?", tienda));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"Tiendas\" SET \"Diseno\" = NULL WHERE \"Id\" = ?", tienda));
        assertThat(jdbc.queryForObject("SELECT \"Paleta\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda)).isEqualTo("OTEMU");
    }
}
