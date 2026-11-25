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
        SmtpEnviadorCorreo enviador = new SmtpEnviadorCorreo(servidor, false, "no-responder@otemu.test");

        enviador.enviar("alguien@prueba.test", "Asunto", "Texto", "<p>Texto</p>");

        verify(servidor, never()).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        verify(servidor, never()).send(org.mockito.ArgumentMatchers.any(jakarta.mail.internet.MimeMessage.class));
    }
}
