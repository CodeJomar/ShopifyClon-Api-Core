package shopify.api.core.modulos.notificacion.correo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Con el envío deshabilitado no se llama al servidor SMTP (criterio de app.correo.habilitado=false). */
class SmtpEnviadorCorreoTest {

    @Test
    void conElEnvioDeshabilitadoNoSeLlamaAlServidor() {
        JavaMailSender servidor = mock(JavaMailSender.class);
        SmtpEnviadorCorreo enviador = new SmtpEnviadorCorreo(servidor, false, "no-responder@shopifyclon.test");

        enviador.enviar("alguien@prueba.test", "Asunto", "Texto");

        verify(servidor, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }
}
