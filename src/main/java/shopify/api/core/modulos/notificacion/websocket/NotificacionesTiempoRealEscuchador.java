package shopify.api.core.modulos.notificacion.websocket;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import shopify.api.core.modulos.envio.evento.EnvioDespachadoEvento;
import shopify.api.core.modulos.envio.evento.EnvioEntregadoEvento;
import shopify.api.core.modulos.envio.modelo.DatosEnvio;
import shopify.api.core.modulos.envio.servicio.EnvioDatosServicio;
import shopify.api.core.modulos.orden.eventos.OrdenCreadaEvento;
import shopify.api.core.modulos.orden.eventos.OrdenPagadaEvento;
import shopify.api.core.modulos.orden.modelo.DatosTiempoRealOrden;
import shopify.api.core.modulos.orden.servicio.OrdenDatosServicio;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;
import shopify.api.core.modulos.tienda.servicio.TiendaContactoServicio;

/**
 * Emite eventos STOMP en tiempo real tras confirmarse la transacción en PostgreSQL:
 * al panel de la tienda ({@code /topic/tienda/{tiendaId}/ordenes}) y al comprador ({@code /topic/ordenes/{tokenAcceso}}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionesTiempoRealEscuchador {

    private final SimpMessagingTemplate mensajeria;
    private final OrdenDatosServicio ordenes;
    private final TiendaContactoServicio tiendas;
    private final EnvioDatosServicio envios;
    private final Clock reloj;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCrearOrden(OrdenCreadaEvento evento) {
        enviarSeguro("ORDEN_CREADA", evento.ordenId(),
                () -> emitirPorOrden("ORDEN_CREADA", evento.ordenId(), null, null, null, false));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alPagarOrden(OrdenPagadaEvento evento) {
        enviarSeguro("ORDEN_PAGADA", evento.ordenId(),
                () -> emitirPorOrden("ORDEN_PAGADA", evento.ordenId(), null, null, null, true));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alCancelarOrden(OrdenCanceladaEvento evento) {
        enviarSeguro("ORDEN_CANCELADA", evento.ordenId(),
                () -> emitirPorOrden("ORDEN_CANCELADA", evento.ordenId(), evento.motivo(), null, null, true));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alDespacharEnvio(EnvioDespachadoEvento evento) {
        enviarSeguro("ENVIO_DESPACHADO", evento.ordenId(), () -> {
            DatosEnvio envio = envios.datos(evento.envioId());
            emitirPorOrden("ENVIO_DESPACHADO", envio.ordenId(), null, evento.envioId(), envio, true);
        });
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alEntregarEnvio(EnvioEntregadoEvento evento) {
        enviarSeguro("ENVIO_ENTREGADO", evento.ordenId(), () -> {
            DatosEnvio envio = envios.datos(evento.envioId());
            emitirPorOrden("ENVIO_ENTREGADO", envio.ordenId(), null, evento.envioId(), envio, true);
        });
    }

    private void emitirPorOrden(
            String tipo,
            UUID ordenId,
            String motivo,
            UUID envioId,
            DatosEnvio envio,
            boolean notificarComprador) {
        DatosTiempoRealOrden orden = ordenes.datosTiempoReal(ordenId).orElse(null);
        if (orden == null) {
            return;
        }
        String moneda = tiendas.contacto(orden.tiendaId()).moneda();
        Instant ocurridoEn = reloj.instant();

        NotificacionOrdenTienda alPanel = new NotificacionOrdenTienda(
                tipo,
                orden.ordenId(),
                orden.tiendaId(),
                orden.numeroOrden(),
                orden.nombreCliente(),
                orden.total(),
                moneda,
                orden.estado(),
                orden.estadoPago(),
                orden.estadoPreparacion(),
                motivo,
                envioId,
                envio != null ? envio.numeroGuia() : null,
                envio != null ? envio.transportista() : null,
                ocurridoEn);
        mensajeria.convertAndSend("/topic/tienda/" + orden.tiendaId() + "/ordenes", alPanel);

        if (notificarComprador && StringUtils.hasText(orden.tokenAcceso())) {
            NotificacionOrdenComprador alComprador = new NotificacionOrdenComprador(
                    tipo,
                    orden.numeroOrden(),
                    orden.estado(),
                    orden.estadoPago(),
                    orden.estadoPreparacion(),
                    motivo,
                    envio != null ? envio.numeroGuia() : null,
                    envio != null ? envio.transportista() : null,
                    envio != null ? envio.urlSeguimiento() : null,
                    ocurridoEn);
            mensajeria.convertAndSend("/topic/ordenes/" + orden.tokenAcceso(), alComprador);
        }
    }

    private static void enviarSeguro(String tipo, UUID ordenId, Runnable tarea) {
        try {
            tarea.run();
        } catch (RuntimeException ex) {
            log.warn("No se pudo emitir la notificación WebSocket {} para orden {}: {}",
                    tipo, ordenId, ex.getClass().getSimpleName());
        }
    }
}
