package shopify.api.core.modulos.notificacion.escuchador;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.modulos.notificacion.correo.EnviadorCorreo;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionRenovadaEvento;
import shopify.api.core.modulos.suscripcion.evento.SuscripcionVencidaEvento;
import shopify.api.core.modulos.tienda.modelo.DatosContactoTienda;
import shopify.api.core.modulos.tienda.servicio.TiendaContactoServicio;

/** Avisos al propietario de la tienda sobre su suscripción (vencida o renovada). Después del commit. */
@Component
@RequiredArgsConstructor
public class NotificacionesSuscripcionEscuchador {

    private final EnviadorCorreo correo;
    private final TiendaContactoServicio tiendas;

    @Value("${app.web.url-base:http://localhost:3000}")
    private String urlBase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alVencer(SuscripcionVencidaEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("suscripción vencida", () -> {
            DatosContactoTienda tienda = tiendas.contacto(evento.tiendaId());
            if (tienda.correoPropietario() == null) {
                return;
            }
            var mensaje = Plantillas.suscripcionVencida(tienda.nombre(), urlBase + "/panel/suscripcion");
            correo.enviar(tienda.correoPropietario(), mensaje.asunto(), mensaje.texto());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alRenovar(SuscripcionRenovadaEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("suscripción renovada", () -> {
            DatosContactoTienda tienda = tiendas.contacto(evento.tiendaId());
            if (tienda.correoPropietario() == null) {
                return;
            }
            var mensaje = Plantillas.suscripcionRenovada(tienda.nombre());
            correo.enviar(tienda.correoPropietario(), mensaje.asunto(), mensaje.texto());
        });
    }
}
