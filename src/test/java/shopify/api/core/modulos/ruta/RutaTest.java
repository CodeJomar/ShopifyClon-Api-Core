package shopify.api.core.modulos.ruta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaConEnvios;

/** A-004, RUT-001 a RUT-005: crear, editar, iniciar y finalizar rutas de reparto, y el detalle para la guía de remisión. */
class RutaTest extends PruebaConEnvios {

    private static final String RUTAS = "/panel/rutas";

    private Map<String, Object> cuerpo(List<UUID> envios) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("fecha", LocalDate.now().plusDays(1).toString());
        cuerpo.put("chofer", "Luis Ramos");
        cuerpo.put("placa", "abc-123");
        cuerpo.put("nota", "Reparto zona sur");
        cuerpo.put("envio_ids", envios);
        return cuerpo;
    }

    private ResultActions crearRuta(List<UUID> envios) throws Exception {
        return postJson(RUTAS, cuerpo(envios), gestorJwt, tienda);
    }

    private UUID rutaCreada(List<UUID> envios) throws Exception {
        return UUID.fromString(cuerpo(crearRuta(envios).andExpect(status().isCreated())).get("datos").get("id").asText());
    }

    private JsonNode detalle(UUID ruta) throws Exception {
        return cuerpo(getConToken(RUTAS + "/" + ruta, gestorJwt, tienda).andExpect(status().isOk())).get("datos");
    }

    private String estadoEnvio(UUID envio) {
        return jdbc.queryForObject("SELECT \"Estado\" FROM \"Envios\" WHERE \"Id\" = ?", String.class, envio);
    }

    @Test
    void crearConTresEnviosRespetaElOrdenYArmaLaGuiaDeRemision() throws Exception {
        UUID a = envioCreado();
        UUID b = envioCreado();
        UUID c = envioCreado();

        JsonNode ruta = cuerpo(crearRuta(List.of(c, a, b))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.codigo").value("RT-0001"))
                .andExpect(jsonPath("$.datos.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.datos.placa").value("ABC-123"))).get("datos");

        JsonNode paradas = ruta.get("paradas");
        assertThat(paradas).hasSize(3);
        assertThat(paradas.get(0).get("envio_id").asText()).isEqualTo(c.toString());
        assertThat(paradas.get(1).get("envio_id").asText()).isEqualTo(a.toString());
        assertThat(paradas.get(2).get("envio_id").asText()).isEqualTo(b.toString());
        assertThat(paradas.get(0).get("posicion").asInt()).isEqualTo(1);
        assertThat(paradas.get(2).get("posicion").asInt()).isEqualTo(3);

        JsonNode primera = paradas.get(0);
        assertThat(primera.get("destinatario").asText()).isEqualTo("Ana Perez");
        assertThat(primera.get("telefono").asText()).isEqualTo("999888777");
        assertThat(primera.get("numero_orden").asText()).startsWith("#");
        assertThat(primera.get("numero_guia").asText()).matches("OT-\\d{8}-[A-Z0-9]{6}");
        assertThat(primera.get("estado_envio").asText()).isEqualTo("CREADO");
        assertThat(primera.get("direccion").get("direccion_linea1").asText()).isEqualTo("Av. Siempre Viva 123");
        assertThat(primera.get("direccion").get("ciudad").asText()).isEqualTo("Lima");
        assertThat(primera.get("latitud").decimalValue()).isEqualByComparingTo("-12.0464");
        assertThat(primera.get("longitud").decimalValue()).isEqualByComparingTo("-77.0428");
        assertThat(primera.get("items")).hasSize(1);
        assertThat(primera.get("items").get(0).get("cantidad").asInt()).isEqualTo(1);
        assertThat(primera.get("items").get(0).get("sku").asText()).startsWith("SKU-");
        assertThat(ruta.get("tienda").get("nombre").asText()).isNotBlank();
        assertThat(ruta.get("tienda").get("correo_electronico").asText()).contains("@");
    }

    @Test
    void elMismoEnvioEnOtraRutaActivaEsRut002() throws Exception {
        UUID envio = envioCreado();
        rutaCreada(List.of(envio));

        crearRuta(List.of(envio))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RUT-002"));
    }

    @Test
    void soloEnviosCreadosOEnTransitoRut001() throws Exception {
        UUID envio = envioCreado();
        postJson("/panel/envios/" + envio + "/despachar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + envio + "/entregar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        crearRuta(List.of(envio))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RUT-001"));
    }

    @Test
    void iniciarLaRutaDespachaLosEnviosCreados() throws Exception {
        UUID creado = envioCreado();
        UUID enTransito = envioCreado();
        postJson("/panel/envios/" + enTransito + "/despachar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        UUID ruta = rutaCreada(List.of(creado, enTransito));

        postJson(RUTAS + "/" + ruta + "/iniciar", Map.of(), gestorJwt, tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_CURSO"));

        assertThat(estadoEnvio(creado)).isEqualTo("EN_TRANSITO");
        assertThat(estadoEnvio(enTransito)).isEqualTo("EN_TRANSITO");
        assertThat(jdbc.queryForObject("SELECT \"FechaDespacho\" IS NOT NULL FROM \"Envios\" WHERE \"Id\" = ?",
                Boolean.class, creado)).isTrue();
        JsonNode eventos = cuerpo(getConToken("/panel/envios/" + creado + "/eventos", gestorJwt, tienda)).get("datos");
        assertThat(eventos.get(0).get("estado").asText()).isEqualTo("EN_TRANSITO");

        postJson(RUTAS + "/" + ruta + "/iniciar", Map.of(), gestorJwt, tienda)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RUT-003"));
    }

    @Test
    void unaRutaEnCursoNoSeEdita() throws Exception {
        UUID envio = envioCreado();
        UUID ruta = rutaCreada(List.of(envio));
        postJson(RUTAS + "/" + ruta + "/iniciar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        putJson(RUTAS + "/" + ruta, cuerpo(List.of(envio)), gestorJwt, tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RUT-005"));
    }

    @Test
    void enBorradorSePuedeEditarYReordenar() throws Exception {
        UUID a = envioCreado();
        UUID b = envioCreado();
        UUID c = envioCreado();
        UUID ruta = rutaCreada(List.of(a, b));

        Map<String, Object> cambios = cuerpo(List.of(c, b, a));
        cambios.put("chofer", "Marta Díaz");
        JsonNode editada = cuerpo(putJson(RUTAS + "/" + ruta, cambios, gestorJwt, tienda).andExpect(status().isOk())).get("datos");

        assertThat(editada.get("chofer").asText()).isEqualTo("Marta Díaz");
        List<String> orden = new ArrayList<>();
        editada.get("paradas").forEach(p -> orden.add(p.get("envio_id").asText()));
        assertThat(orden).containsExactly(c.toString(), b.toString(), a.toString());
        // el mismo envío de la misma ruta no cuenta como RUT-002 al editarla
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"ParadasRuta\" WHERE \"RutaId\" = ? AND \"Eliminado\" = FALSE",
                Integer.class, ruta)).isEqualTo(3);
    }

    @Test
    void finalizarSoloDesdeEnCursoYLiberaLosEnvios() throws Exception {
        UUID envio = envioCreado();
        UUID ruta = rutaCreada(List.of(envio));

        postJson(RUTAS + "/" + ruta + "/finalizar", Map.of(), gestorJwt, tienda)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RUT-004"));

        postJson(RUTAS + "/" + ruta + "/iniciar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson(RUTAS + "/" + ruta + "/finalizar", Map.of(), gestorJwt, tienda)
                .andExpect(status().isOk()).andExpect(jsonPath("$.datos.estado").value("FINALIZADA"));

        // con la parada pendiente, el envío queda como estaba y se puede volver a rutear
        assertThat(estadoEnvio(envio)).isEqualTo("EN_TRANSITO");
        rutaCreada(List.of(envio));
    }

    @Test
    void disponiblesExcluyeLosEnviosEnRutasActivas() throws Exception {
        UUID libre = envioCreado();
        UUID ocupado = envioCreado();
        rutaCreada(List.of(ocupado));

        JsonNode lista = cuerpo(getConToken("/panel/envios/disponibles-para-ruta", gestorJwt, tienda)
                .andExpect(status().isOk())).get("datos");

        List<String> ids = new ArrayList<>();
        lista.forEach(e -> ids.add(e.get("envio_id").asText()));
        assertThat(ids).contains(libre.toString()).doesNotContain(ocupado.toString());
        JsonNode item = lista.get(0);
        assertThat(item.get("destinatario").asText()).isEqualTo("Ana Perez");
        assertThat(item.get("ciudad").asText()).isEqualTo("Lima");
        assertThat(item.get("total_items").asInt()).isEqualTo(1);
        assertThat(item.get("estado_envio").asText()).isEqualTo("CREADO");
    }

    @Test
    void elListadoFiltraPorEstadoYFechaYCuentaLasParadas() throws Exception {
        UUID uno = envioCreado();
        UUID dos = envioCreado();
        UUID borrador = rutaCreada(List.of(uno, dos));
        UUID otra = rutaCreada(List.of(envioCreado()));
        postJson(RUTAS + "/" + otra + "/iniciar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + uno + "/despachar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson("/panel/envios/" + uno + "/entregar", Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        JsonNode todas = cuerpo(getConToken(RUTAS, gestorJwt, tienda).andExpect(status().isOk())).get("datos");
        assertThat(todas.get("datos")).hasSize(2);

        JsonNode enBorrador = cuerpo(getConToken(RUTAS + "?estado=BORRADOR", gestorJwt, tienda)).get("datos").get("datos");
        assertThat(enBorrador).hasSize(1);
        assertThat(enBorrador.get(0).get("id").asText()).isEqualTo(borrador.toString());
        assertThat(enBorrador.get(0).get("total_paradas").asInt()).isEqualTo(2);
        assertThat(enBorrador.get(0).get("paradas_entregadas").asInt()).isEqualTo(1);

        String manana = LocalDate.now().plusDays(1).toString();
        assertThat(cuerpo(getConToken(RUTAS + "?fecha=" + manana, gestorJwt, tienda)).get("datos").get("datos")).hasSize(2);
        assertThat(cuerpo(getConToken(RUTAS + "?fecha=2001-01-01", gestorJwt, tienda)).get("datos").get("datos")).isEmpty();
    }

    @Test
    void validacionesDeLaSolicitud() throws Exception {
        UUID envio = envioCreado();

        Map<String, Object> pasada = cuerpo(List.of(envio));
        pasada.put("fecha", "2001-01-01");
        postJson(RUTAS, pasada, gestorJwt, tienda).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));

        postJson(RUTAS, cuerpo(List.of(envio, envio)), gestorJwt, tienda).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));

        Map<String, Object> vacia = cuerpo(List.of());
        vacia.put("chofer", " ");
        postJson(RUTAS, vacia, gestorJwt, tienda).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.validaciones.envio_ids").exists())
                .andExpect(jsonPath("$.validaciones.chofer").exists());
    }

    @Test
    void otraTiendaNoVeNiModificaLasRutasNiUsaSusEnvios() throws Exception {
        UUID envio = envioCreado();
        UUID ruta = rutaCreada(List.of(envio));
        UUID otraTienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String intruso = jwtDePersonal(otraTienda, "GESTOR_PEDIDOS");

        getConToken(RUTAS + "/" + ruta, intruso, otraTienda)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("RUT-404"));
        postJson(RUTAS + "/" + ruta + "/iniciar", Map.of(), intruso, otraTienda).andExpect(status().isNotFound());
        putJson(RUTAS + "/" + ruta, cuerpo(List.of(envio)), intruso, otraTienda).andExpect(status().isNotFound());
        postJson(RUTAS, cuerpo(List.of(envio)), intruso, otraTienda)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ENV-404"));
        assertThat(cuerpo(getConToken(RUTAS, intruso, otraTienda)).get("datos").get("datos")).isEmpty();
    }

    @Test
    void sinPermisoDePedidosNoSeAccede() throws Exception {
        String catalogo = jwtDePersonal(tienda, "GESTOR_CATALOGO");

        getConToken(RUTAS, catalogo, tienda).andExpect(status().isForbidden());
        getConToken("/panel/envios/disponibles-para-ruta", catalogo, tienda).andExpect(status().isForbidden());
    }

    @Test
    void elCodigoEsCorrelativoPorTienda() throws Exception {
        UUID primera = rutaCreada(List.of(envioCreado()));
        UUID segunda = rutaCreada(List.of(envioCreado()));

        assertThat(detalle(primera).get("codigo").asText()).isEqualTo("RT-0001");
        assertThat(detalle(segunda).get("codigo").asText()).isEqualTo("RT-0002");
    }
}
