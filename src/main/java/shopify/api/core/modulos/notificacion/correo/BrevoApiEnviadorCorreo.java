package shopify.api.core.modulos.notificacion.correo;

import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envío por la API HTTP de Brevo (puerto 443). Sirve donde el puerto SMTP está bloqueado (por ejemplo Railway en
 * planes de prueba). Se activa con {@code app.correo.proveedor=brevo-api}. Como en el envío SMTP, ni destinatario ni
 * contenido van a los logs, y con {@code app.correo.habilitado=false} no se llama a Brevo.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "brevo-api")
public class BrevoApiEnviadorCorreo implements EnviadorCorreo {

    static final String URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient cliente;
    private final boolean habilitado;
    private final String remitente;
    private final String claveApi;

    @Autowired
    public BrevoApiEnviadorCorreo(@Value("${app.correo.habilitado:true}") boolean habilitado,
                                  @Value("${app.correo.remitente:no-responder@shopifyclon.test}") String remitente,
                                  @Value("${app.correo.brevo.api-key:}") String claveApi) {
        this(RestClient.create(), habilitado, remitente, claveApi);
    }

    BrevoApiEnviadorCorreo(RestClient cliente, boolean habilitado, String remitente, String claveApi) {
        this.cliente = cliente;
        this.habilitado = habilitado;
        this.remitente = remitente;
        this.claveApi = claveApi;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        if (!habilitado) {
            log.info("Correo omitido: el envío de correos está deshabilitado");
            return;
        }
        if (claveApi == null || claveApi.isBlank()) {
            throw new IllegalStateException("Falta app.correo.brevo.api-key (BREVO_API_KEY)");
        }
        Map<String, Object> cuerpo = Map.of(
                "sender", Map.of("email", remitente, "name", "OTemu"),
                "to", List.of(Map.of("email", para)),
                "subject", asunto,
                "textContent", texto);
        cliente.post().uri(URL)
                .header("api-key", claveApi)
                .header("accept", "application/json")
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity();
        log.info("Correo enviado");
    }
}
