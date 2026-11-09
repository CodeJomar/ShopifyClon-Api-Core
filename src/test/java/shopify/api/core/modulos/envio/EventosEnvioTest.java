package shopify.api.core.modulos.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaConEnvios;

/** A-004: cada transición del envío deja un evento; NO_ENTREGADO exige nota; la entrega admite foto https opcional. */
class EventosEnvioTest extends PruebaConEnvios {

    private static String ruta(UUID envio, String accion) {
        return "/panel/envios/" + envio + "/" + accion;
    }

    private List<String> estadosDelHistorial(UUID envio, String jwt, UUID tiendaId) throws Exception {
        JsonNode lista = cuerpo(getConToken(ruta(envio, "eventos"), jwt, tiendaId).andExpect(status().isOk())).get("datos");
        List<String> estados = new ArrayList<>();
        lista.forEach(e -> estados.add(e.get("estado").asText()));
        return estados;
    }

    @Test
    void crearElEnvioDejaElEventoCreado() throws Exception {
        UUID envio = envioCreado();

        assertThat(estadosDelHistorial(envio, gestorJwt, tienda)).containsExactly("CREADO");
    }

    @Test
    void cadaTransicionAgregaSuEventoYElHistorialVieneMasRecientePrimero() throws Exception {
        UUID envio = envioCreado();

        postJson(ruta(envio, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson(ruta(envio, "entregar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        assertThat(estadosDelHistorial(envio, gestorJwt, tienda)).containsExactly("ENTREGADO", "EN_TRANSITO", "CREADO");
    }

    @Test
    void devolverTambienDejaSuEvento() throws Exception {
        UUID envio = envioCreado();
        postJson(ruta(envio, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        postJson(ruta(envio, "devolver"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        assertThat(estadosDelHistorial(envio, gestorJwt, tienda)).containsExactly("DEVUELTO", "EN_TRANSITO", "CREADO");
    }

    @Test
    void entregarConFotoYNotaLasGuardaEnElEvento() throws Exception {
        UUID envio = envioCreado();
        postJson(ruta(envio, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        postJson(ruta(envio, "entregar"), Map.of("foto_url", "https://img.test/entrega.jpg", "nota", "Recibió la portera"),
                gestorJwt, tienda).andExpect(status().isOk());

        JsonNode ultimo = cuerpo(getConToken(ruta(envio, "eventos"), gestorJwt, tienda)).get("datos").get(0);
        assertThat(ultimo.get("estado").asText()).isEqualTo("ENTREGADO");
        assertThat(ultimo.get("foto_url").asText()).isEqualTo("https://img.test/entrega.jpg");
        assertThat(ultimo.get("nota").asText()).isEqualTo("Recibió la portera");
        assertThat(ultimo.get("fecha_evento").asText()).isNotBlank();
    }

    @Test
    void entregarSinCuerpoFuncionaYLaFotoDebeSerHttps() throws Exception {
        UUID sinCuerpo = envioCreado();
        postJson(ruta(sinCuerpo, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(ruta(sinCuerpo, "entregar"))
                        .header("Authorization", "Bearer " + gestorJwt).header("X-Tienda-Id", tienda.toString()))
                .andExpect(status().isOk());
        JsonNode evento = cuerpo(getConToken(ruta(sinCuerpo, "eventos"), gestorJwt, tienda)).get("datos").get(0);
        assertThat(evento.has("foto_url")).isFalse();

        UUID conFotoMala = envioCreado();
        postJson(ruta(conFotoMala, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        postJson(ruta(conFotoMala, "entregar"), Map.of("foto_url", "http://inseguro.test/x.jpg"), gestorJwt, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
        assertThat(estadosDelHistorial(conFotoMala, gestorJwt, tienda)).containsExactly("EN_TRANSITO", "CREADO");
    }

    @Test
    void noEntregadoExigeNotaYNoCambiaElEstadoDelEnvio() throws Exception {
        UUID envio = envioCreado();
        postJson(ruta(envio, "despachar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());

        postJson(ruta(envio, "no-entregado"), Map.of(), gestorJwt, tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VAL-001"));
        postJson(ruta(envio, "no-entregado"), Map.of("nota", "  "), gestorJwt, tienda)
                .andExpect(status().isUnprocessableEntity());

        postJson(ruta(envio, "no-entregado"), Map.of("nota", "Nadie en casa"), gestorJwt, tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("EN_TRANSITO"));

        JsonNode ultimo = cuerpo(getConToken(ruta(envio, "eventos"), gestorJwt, tienda)).get("datos").get(0);
        assertThat(ultimo.get("estado").asText()).isEqualTo("NO_ENTREGADO");
        assertThat(ultimo.get("nota").asText()).isEqualTo("Nadie en casa");

        // se puede reintentar la entrega después
        postJson(ruta(envio, "entregar"), Map.of(), gestorJwt, tienda).andExpect(status().isOk());
        assertThat(estadosDelHistorial(envio, gestorJwt, tienda))
                .containsExactly("ENTREGADO", "NO_ENTREGADO", "EN_TRANSITO", "CREADO");
    }

    @Test
    void noEntregadoSoloEnTransito() throws Exception {
        UUID envio = envioCreado();

        postJson(ruta(envio, "no-entregado"), Map.of("nota", "Aún no sale"), gestorJwt, tienda)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ENV-004"));
        assertThat(estadosDelHistorial(envio, gestorJwt, tienda)).containsExactly("CREADO");
    }

    @Test
    void elEnvioAutomaticoRecibeNumeroDeGuiaConFormato() throws Exception {
        UUID envio = envioCreado();

        String guia = jdbc.queryForObject("SELECT \"NumeroGuia\" FROM \"Envios\" WHERE \"Id\" = ?", String.class, envio);

        assertThat(guia).matches("OT-\\d{8}-[A-Z0-9]{6}");
    }

    @Test
    void unaGuiaManualSeRespeta() throws Exception {
        UUID orden = ordenFisicaPagada();

        UUID envio = UUID.fromString(cuerpo(postJson("/panel/ordenes/" + orden + "/envio",
                Map.of("empresa_transporte", "Olva", "numero_guia", "MANUAL-123"), gestorJwt, tienda)
                .andExpect(status().isCreated())).get("datos").get("id").asText());

        assertThat(jdbc.queryForObject("SELECT \"NumeroGuia\" FROM \"Envios\" WHERE \"Id\" = ?", String.class, envio))
                .isEqualTo("MANUAL-123");
    }

    @Test
    void otraTiendaNoVeNiModificaElEnvio() throws Exception {
        UUID envio = envioCreado();
        UUID otraTienda = crearTiendaOperativa(crearUsuarioActivo(false));
        String intruso = jwtDePersonal(otraTienda, "GESTOR_PEDIDOS");

        getConToken(ruta(envio, "eventos"), intruso, otraTienda)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ENV-404"));
        postJson(ruta(envio, "no-entregado"), Map.of("nota", "x"), intruso, otraTienda)
                .andExpect(status().isNotFound());
        postJson(ruta(envio, "entregar"), Map.of(), intruso, otraTienda)
                .andExpect(status().isNotFound());
    }
}
