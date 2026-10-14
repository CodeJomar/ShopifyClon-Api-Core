package shopify.api.core.modules.notificacion.application.casouso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.comun.evento.CodigoRecuperacionGeneradoEvento;
import shopify.api.core.comun.evento.UsuarioRegistradoEvento;

@Slf4j
@Component
@RequiredArgsConstructor
public class EscuchadorNotificacionesUsuario {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alRegistrarUsuario(UsuarioRegistradoEvento evento) {
        if (mailSender != null) {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setTo(evento.getCorreoElectronico());
                mensaje.setSubject("Activa tu cuenta en Shopify Clon");
                mensaje.setText("Hola " + evento.getNombres() + ",\n\nActiva tu cuenta con este enlace:\n"
                        + "http://localhost:8080/api/v1/usuarios/activar-cuenta?token=" + evento.getTokenActivacion());
                mailSender.send(mensaje);
                log.info("Correo de activación enviado a {}", evento.getCorreoElectronico());
            } catch (Exception e) {
                log.error("Fallo al enviar correo a {}: {}", evento.getCorreoElectronico(), e.getMessage());
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alSolicitarRecuperacion(CodigoRecuperacionGeneradoEvento evento) {
        if (mailSender != null) {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setTo(evento.getCorreoElectronico());
                mensaje.setSubject("Código de recuperación de contraseña");
                mensaje.setText("Hola " + evento.getNombres() + ",\n\nTu código de recuperación es: " + evento.getCodigoOtp());
                mailSender.send(mensaje);
                log.info("Correo de recuperación enviado a {}", evento.getCorreoElectronico());
            } catch (Exception e) {
                log.error("Fallo al enviar correo a {}: {}", evento.getCorreoElectronico(), e.getMessage());
            }
        }
    }
}