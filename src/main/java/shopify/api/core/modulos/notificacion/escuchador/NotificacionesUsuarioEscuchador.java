package shopify.api.core.modulos.notificacion.escuchador;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.modulos.notificacion.correo.EnviadorCorreo;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas;
import shopify.api.core.modulos.usuario.evento.CodigoActivacionEmitidoEvento;
import shopify.api.core.modulos.usuario.evento.CodigoRecuperacionEmitidoEvento;

/** Correos de usuario: código de activación (24 h) y código de recuperación (30 min). */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionesUsuarioEscuchador {

    private final EnviadorCorreo correo;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEmitirCodigoActivacion(CodigoActivacionEmitidoEvento evento) {
        enviarSeguro("activación", () -> {
            var mensaje = Plantillas.activacion(evento.nombres(), evento.codigo());
            correo.enviar(evento.correo(), mensaje.asunto(), mensaje.texto());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEmitirCodigoRecuperacion(CodigoRecuperacionEmitidoEvento evento) {
        enviarSeguro("recuperación", () -> {
            var mensaje = Plantillas.recuperacion(evento.nombres(), evento.codigo());
            correo.enviar(evento.correo(), mensaje.asunto(), mensaje.texto());
        });
    }

    /** Un fallo de correo no debe tumbar nada: se registra el tipo y la clase del error, nunca el contenido. */
    static void enviarSeguro(String tipo, Runnable envio) {
        try {
            envio.run();
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el correo de {}: {}", tipo, ex.getClass().getSimpleName());
        }
    }
}
