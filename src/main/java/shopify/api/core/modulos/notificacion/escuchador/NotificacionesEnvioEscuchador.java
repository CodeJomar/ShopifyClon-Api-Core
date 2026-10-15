package shopify.api.core.modulos.notificacion.escuchador;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.modulos.envio.evento.EnvioDespachadoEvento;
import shopify.api.core.modulos.envio.evento.EnvioEntregadoEvento;
import shopify.api.core.modulos.envio.modelo.DatosEnvio;
import shopify.api.core.modulos.envio.servicio.EnvioDatosServicio;
import shopify.api.core.modulos.notificacion.correo.EnviadorCorreo;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas;
import shopify.api.core.modulos.orden.modelo.DatosNotificacionOrden;
import shopify.api.core.modulos.orden.servicio.OrdenDatosServicio;

/** Correos al comprador cuando su envío sale (con guía) y cuando llega. Después del commit. */
@Component
@RequiredArgsConstructor
public class NotificacionesEnvioEscuchador {

    private final EnviadorCorreo correo;
    private final EnvioDatosServicio envios;
    private final OrdenDatosServicio ordenes;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alDespachar(EnvioDespachadoEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("envío despachado", () -> {
            DatosEnvio envio = envios.datos(evento.envioId());
            DatosNotificacionOrden orden = ordenes.datosNotificacion(envio.ordenId());
            var mensaje = Plantillas.envioDespachado(orden.numeroOrden(), orden.nombreCliente(), envio.transportista(),
                    envio.numeroGuia(), envio.urlSeguimiento());
            correo.enviar(orden.correoCliente(), mensaje.asunto(), mensaje.texto());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEntregar(EnvioEntregadoEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("envío entregado", () -> {
            DatosEnvio envio = envios.datos(evento.envioId());
            DatosNotificacionOrden orden = ordenes.datosNotificacion(envio.ordenId());
            var mensaje = Plantillas.envioEntregado(orden.numeroOrden(), orden.nombreCliente());
            correo.enviar(orden.correoCliente(), mensaje.asunto(), mensaje.texto());
        });
    }
}
