package shopify.api.core.modulos.notificacion.correo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Envío por la API de Brevo: arma la petición esperada y respeta el interruptor y la clave. */
class BrevoApiEnviadorCorreoTest {

    @Test
    void mandaElCorreoALaApiDeBrevoConLaClave() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(BrevoApiEnviadorCorreo.URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.sender.email").value("remitente@prueba.test"))
                .andExpect(jsonPath("$.to[0].email").value("alguien@prueba.test"))
                .andExpect(jsonPath("$.subject").value("Asunto"))
                .andExpect(jsonPath("$.textContent").value("Texto"))
                .andRespond(withSuccess("{\"messageId\":\"x\"}", MediaType.APPLICATION_JSON));

        new BrevoApiEnviadorCorreo(constructor.build(), true, "remitente@prueba.test", "clave-de-prueba")
                .enviar("alguien@prueba.test", "Asunto", "Texto");

        servidor.verify();
    }

    @Test
    void conElEnvioDeshabilitadoNoLlamaABrevo() {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();

        new BrevoApiEnviadorCorreo(constructor.build(), false, "r@prueba.test", "clave").enviar("a@prueba.test", "A", "T");

        servidor.verify();
    }

    @Test
    void sinClaveDeApiFallaConUnMensajeClaro() {
        BrevoApiEnviadorCorreo enviador = new BrevoApiEnviadorCorreo(RestClient.create(), true, "r@prueba.test", " ");

        assertThatThrownBy(() -> enviador.enviar("a@prueba.test", "A", "T"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("BREVO_API_KEY");
    }
}
