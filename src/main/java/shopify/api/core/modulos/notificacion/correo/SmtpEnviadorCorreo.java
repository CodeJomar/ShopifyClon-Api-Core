package shopify.api.core.modulos.notificacion.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío por SMTP. Con {@code app.correo.habilitado=false} solo registra que el correo se omitió: ni destinatario ni
 * contenido van a los logs.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "smtp", matchIfMissing = true)
public class SmtpEnviadorCorreo implements EnviadorCorreo {

    private final JavaMailSender correo;
    private final boolean habilitado;
    private final String remitente;

    public SmtpEnviadorCorreo(JavaMailSender correo,
                              @Value("${app.correo.habilitado:true}") boolean habilitado,
                              @Value("${app.correo.remitente:no-responder@otemu.test}") String remitente) {
        this.correo = correo;
        this.habilitado = habilitado;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto, String html) {
        if (!habilitado) {
            log.info("Correo omitido: el envío de correos está deshabilitado");
            return;
        }
        if (html == null) {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(remitente);
            mensaje.setTo(para);
            mensaje.setSubject(asunto);
            mensaje.setText(texto);
            correo.send(mensaje);
        } else {
            try {
                MimeMessage mime = correo.createMimeMessage();
                MimeMessageHelper ayudante = new MimeMessageHelper(mime, true, "UTF-8");
                ayudante.setFrom(remitente, "OTemu");
                ayudante.setTo(para);
                ayudante.setSubject(asunto);
                ayudante.setText(texto, html);
                correo.send(mime);
            } catch (MessagingException | UnsupportedEncodingException ex) {
                throw new MailPreparationException("No se pudo preparar el correo", ex);
            }
        }
        log.info("Correo enviado");
    }
}
