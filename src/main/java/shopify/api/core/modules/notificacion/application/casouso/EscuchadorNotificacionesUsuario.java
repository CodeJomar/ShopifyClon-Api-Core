package shopify.api.core.modules.notificacion.application.casouso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CodigoRecuperacionEmitidoEvento;

@Slf4j
@Component
public class EscuchadorNotificacionesUsuario {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEmitirCodigoActivacion(CodigoActivacionEmitidoEvento evento) {
        enviar(evento.correo(), "Activa tu cuenta en Shopify Clon",
                "Hola " + evento.nombres() + ",\n\nTu código de activación es: " + evento.codigo()
                        + " (vence en 24 horas)", "activación");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEmitirCodigoRecuperacion(CodigoRecuperacionEmitidoEvento evento) {
        enviar(evento.correo(), "Código de recuperación de contraseña",
                "Hola " + evento.nombres() + ",\n\nTu código para restablecer la contraseña es: " + evento.codigo()
                        + " (vence en 30 minutos)", "recuperación");
    }

    private void enviar(String correo, String asunto, String texto, String tipo) {
        if (mailSender == null) {
            return;
        }
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setTo(correo);
            mensaje.setSubject(asunto);
            mensaje.setText(texto);
            mailSender.send(mensaje);
            log.info("Correo de {} enviado a {}", tipo, correo);
        } catch (Exception e) {
            log.error("Fallo al enviar el correo de {} a {}: {}", tipo, correo, e.getMessage());
        }
    }
}
