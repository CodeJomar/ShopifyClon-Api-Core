package shopify.api.core.modulos.notificacion.correo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envío por SMTP. Con {@code app.correo.habilitado=false} solo registra que el correo se omitió: ni destinatario ni
 * contenido van a los logs.
 */
@Slf4j
@Component
public class SmtpEnviadorCorreo implements EnviadorCorreo {

    private final JavaMailSender correo;
    private final boolean habilitado;
    private final String remitente;

    public SmtpEnviadorCorreo(JavaMailSender correo,
                              @Value("${app.correo.habilitado:true}") boolean habilitado,
                              @Value("${app.correo.remitente:no-responder@shopifyclon.test}") String remitente) {
        this.correo = correo;
        this.habilitado = habilitado;
        this.remitente = remitente;
    }

    @Override
    public void enviar(String para, String asunto, String texto) {
        if (!habilitado) {
            log.info("Correo omitido: el envío de correos está deshabilitado");
            return;
        }
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(para);
        mensaje.setSubject(asunto);
        mensaje.setText(texto);
        correo.send(mensaje);
        log.info("Correo enviado");
    }
}
