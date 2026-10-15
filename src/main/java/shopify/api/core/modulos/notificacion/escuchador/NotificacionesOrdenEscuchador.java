package shopify.api.core.modulos.notificacion.escuchador;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import shopify.api.core.modulos.notificacion.correo.EnviadorCorreo;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas;
import shopify.api.core.modulos.orden.eventos.OrdenPagadaEvento;
import shopify.api.core.modulos.orden.eventos.OrdenCreadaEvento;
import shopify.api.core.modulos.orden.modelo.DatosNotificacionOrden;
import shopify.api.core.modulos.orden.servicio.OrdenDatosServicio;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;
import shopify.api.core.modulos.tienda.modelo.DatosContactoTienda;
import shopify.api.core.modulos.tienda.servicio.TiendaContactoServicio;

/** Correos de la orden: creada (comprador y tienda), pagada y cancelada. Todos después del commit. */
@Component
@RequiredArgsConstructor
public class NotificacionesOrdenEscuchador {

    private final EnviadorCorreo correo;
    private final OrdenDatosServicio ordenes;
    private final TiendaContactoServicio tiendas;

    @Value("${app.web.url-base:http://localhost:3000}")
    private String urlBase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrearOrden(OrdenCreadaEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("orden creada", () -> {
            DatosNotificacionOrden orden = ordenes.datosNotificacion(evento.ordenId());
            DatosContactoTienda tienda = tiendas.contacto(orden.tiendaId());
            String enlace = urlBase + "/" + tienda.subdominio() + "/ordenes/" + orden.tokenAcceso();
            var alComprador = Plantillas.ordenCreadaComprador(orden.numeroOrden(), orden.nombreCliente(), orden.total(),
                    tienda.moneda(), enlace);
            correo.enviar(orden.correoCliente(), alComprador.asunto(), alComprador.texto());
            if (tienda.correoContacto() != null) {
                var alaTienda = Plantillas.ordenCreadaTienda(orden.numeroOrden(), tienda.nombre(), orden.total(),
                        tienda.moneda());
                correo.enviar(tienda.correoContacto(), alaTienda.asunto(), alaTienda.texto());
            }
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alPagarOrden(OrdenPagadaEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("pago confirmado", () -> {
            DatosNotificacionOrden orden = ordenes.datosNotificacion(evento.ordenId());
            var mensaje = Plantillas.ordenPagada(orden.numeroOrden(), orden.nombreCliente());
            correo.enviar(orden.correoCliente(), mensaje.asunto(), mensaje.texto());
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCancelarOrden(OrdenCanceladaEvento evento) {
        NotificacionesUsuarioEscuchador.enviarSeguro("orden cancelada", () -> {
            DatosNotificacionOrden orden = ordenes.datosNotificacion(evento.ordenId());
            var mensaje = Plantillas.ordenCancelada(orden.numeroOrden(), orden.nombreCliente(), evento.motivo());
            correo.enviar(orden.correoCliente(), mensaje.asunto(), mensaje.texto());
        });
    }
}
