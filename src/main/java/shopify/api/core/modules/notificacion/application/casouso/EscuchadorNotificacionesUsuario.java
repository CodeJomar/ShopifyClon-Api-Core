package shopify.api.core.modules.notificacion.application.casouso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import shopify.api.core.shared.event.CodigoRecuperacionGeneradoEvento;
import shopify.api.core.shared.event.UsuarioRegistradoEvento;

@Slf4j
@Component
@RequiredArgsConstructor
public class EscuchadorNotificacionesUsuario {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Async
    @EventListener
    public void alRegistrarUsuario(UsuarioRegistradoEvento evento) {
        log.info("=================================================================");
        log.info("📧 [NOTIFICACIÓN] Enviar enlace de activación a: {}", evento.getCorreoElectronico());
        log.info("🔗 Token de activación: {}", evento.getTokenActivacion());
        log.info("URL: http://localhost:8080/api/v1/usuarios/activar-cuenta?token={}", evento.getTokenActivacion());
        log.info("=================================================================");

        if (mailSender != null) {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setTo(evento.getCorreoElectronico());
                mensaje.setSubject("Activa tu cuenta en Shopify Clon");
                mensaje.setText("Hola " + evento.getNombres() + ",\n\nActiva tu cuenta con este enlace:\n"
                        + "http://localhost:8080/api/v1/usuarios/activar-cuenta?token=" + evento.getTokenActivacion());
                mailSender.send(mensaje);
            } catch (Exception e) {
                log.error("Fallo al enviar correo real: {}", e.getMessage());
            }
        }
    }

    @Async
    @EventListener
    public void alSolicitarRecuperacion(CodigoRecuperacionGeneradoEvento evento) {
        log.info("=================================================================");
        log.info("🔑 [NOTIFICACIÓN] Código OTP de recuperación para: {}", evento.getCorreoElectronico());
        log.info("🔢 CÓDIGO DE VERIFICACIÓN (Válido 15 min): {}", evento.getCodigoOtp());
        log.info("=================================================================");

        if (mailSender != null) {
            try {
                SimpleMailMessage mensaje = new SimpleMailMessage();
                mensaje.setTo(evento.getCorreoElectronico());
                mensaje.setSubject("Código de recuperación de contraseña");
                mensaje.setText("Hola " + evento.getNombres() + ",\n\nTu código de recuperación es: " + evento.getCodigoOtp());
                mailSender.send(mensaje);
            } catch (Exception e) {
                log.error("Fallo al enviar correo real: {}", e.getMessage());
            }
        }
    }
}