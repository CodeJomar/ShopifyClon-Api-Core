package shopify.api.core.modulos.pago.servicio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.orden.eventos.PedidoPagadoEvento;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.pasarela.EventoPasarela;
import shopify.api.core.modulos.pago.pasarela.PasarelaEnLinea;
import shopify.api.core.modulos.pago.repositorio.PagoRepositorio;

/**
 * PAG-003: webhook de la pasarela. Verifica la firma, y confirma el pago una sola vez: el bloqueo del pago serializa
 * los envíos repetidos, y el segundo ve el pago ya APROBADO y no hace nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookStripeServicio {

    static final String COBRO_EXITOSO = "payment_intent.succeeded";
    static final String COBRO_FALLIDO = "payment_intent.payment_failed";

    private final PasarelaEnLinea pasarela;
    private final PagoRepositorio pagos;
    private final OrdenServicio ordenes;
    private final ConfirmacionPagoServicio confirmacion;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;

    /** Procesa un evento verificado. Firma inválida lanza 403 PAG-003 desde la pasarela. */
    public void procesar(String cuerpo, String firma) {
        EventoPasarela evento = pasarela.verificarWebhook(cuerpo, firma);
        if (evento.intentoId() == null) {
            return;
        }
        if (COBRO_EXITOSO.equals(evento.tipo())) {
            confirmarCobro(evento);
        } else if (COBRO_FALLIDO.equals(evento.tipo())) {
            rechazarCobro(evento);
        } else {
            log.info("Webhook de pagos ignorado: tipo {}", evento.tipo());
        }
    }

    private void confirmarCobro(EventoPasarela evento) {
        // Un cobro de pedido tiene un pago por orden hija con la misma transacción; uno suelto tiene uno solo
        List<PagoEntidad> candidatos = pagos.findByPasarelaPagoAndTransaccionIdAndEliminadoFalseOrderByOrdenIdAsc(
                PasarelaPago.STRIPE, evento.intentoId());
        if (candidatos.isEmpty()) {
            log.warn("Cobro {} sin pago registrado; se ignora", evento.intentoId());
            return;
        }
        List<UUID> pagoIds = candidatos.stream().map(PagoEntidad::getId).toList();
        Map<UUID, UUID> tiendaPorOrden = new HashMap<>();
        for (PagoEntidad candidato : candidatos) {
            UUID tiendaId = ordenes.tiendaDeOrden(candidato.getOrdenId()).orElse(null);
            if (tiendaId == null) {
                log.warn("Cobro {} de una orden inexistente; se ignora", evento.intentoId());
                return;
            }
            tiendaPorOrden.put(candidato.getOrdenId(), tiendaId);
        }
        transaccion.executeWithoutResult(estado -> confirmarEnTransaccion(pagoIds, tiendaPorOrden, evento));
    }

    /**
     * Confirma todos los pagos del cobro en una sola transacción (bloqueos en orden de orden, sin cruces). Un evento
     * repetido ve los pagos ya APROBADOS y no hace nada. El monto cobrado debe ser la suma de las órdenes.
     */
    private void confirmarEnTransaccion(List<UUID> pagoIds, Map<UUID, UUID> tiendaPorOrden, EventoPasarela evento) {
        List<PagoEntidad> bloqueados = new ArrayList<>();
        for (UUID pagoId : pagoIds) {
            pagos.bloquearPorId(pagoId).ifPresent(bloqueados::add);
        }
        // Un fallo no cierra el intento de Stripe: el comprador puede reintentar con otra tarjeta y el cobro llegar después.
        // Por eso un pago RECHAZADO por fallo todavía se confirma si el mismo intento termina cobrándose.
        List<PagoEntidad> porConfirmar = bloqueados.stream()
                .filter(p -> p.getEstado() == EstadoRegistroPago.PENDIENTE || p.getEstado() == EstadoRegistroPago.RECHAZADO)
                .toList();
        if (porConfirmar.isEmpty()) {
            // Repetido (ya APROBADO) o ya resuelto: no hay efectos
            return;
        }
        long esperado = 0;
        for (PagoEntidad pago : porConfirmar) {
            OrdenEstado orden = ordenes.bloquear(tiendaPorOrden.get(pago.getOrdenId()), pago.getOrdenId());
            esperado += orden.total().movePointRight(2).longValueExact();
        }
        if (evento.montoCentavos() != esperado) {
            for (PagoEntidad pago : porConfirmar) {
                pago.setDetallesRespuesta(detalles(evento));
                pago.setEstado(EstadoRegistroPago.RECHAZADO);
                pagos.saveAndFlush(pago);
            }
            log.error("Cobro {} por {} centavos no coincide con el total {}; pagos rechazados",
                    evento.intentoId(), evento.montoCentavos(), esperado);
            return;
        }
        UUID pedidoId = null;
        for (PagoEntidad pago : porConfirmar) {
            pago.setDetallesRespuesta(detalles(evento));
            pago.setEstado(EstadoRegistroPago.APROBADO);
            pagos.saveAndFlush(pago);
            confirmacion.confirmar(tiendaPorOrden.get(pago.getOrdenId()), pago.getId());
            pedidoId = ordenes.pedidoDeOrden(pago.getOrdenId()).orElse(pedidoId);
        }
        if (pedidoId != null) {
            eventos.publishEvent(new PedidoPagadoEvento(pedidoId));
        }
    }

    private void rechazarCobro(EventoPasarela evento) {
        pagos.findByPasarelaPagoAndTransaccionIdAndEliminadoFalseOrderByOrdenIdAsc(PasarelaPago.STRIPE, evento.intentoId())
                .forEach(candidato -> transaccion.executeWithoutResult(
                        estado -> rechazarEnTransaccion(candidato.getId(), evento)));
    }

    private void rechazarEnTransaccion(UUID pagoId, EventoPasarela evento) {
        pagos.bloquearPorId(pagoId).ifPresent(pago -> {
            if (pago.getEstado() == EstadoRegistroPago.PENDIENTE) {
                pago.setEstado(EstadoRegistroPago.RECHAZADO);
                pago.setDetallesRespuesta(detalles(evento));
                pagos.saveAndFlush(pago);
            }
        });
    }

    /** PAG-009: solo el resumen del evento, nunca datos de tarjeta. */
    private static Map<String, Object> detalles(EventoPasarela evento) {
        return Map.of("tipo", evento.tipo(), "intento_id", evento.intentoId(), "monto", evento.montoCentavos());
    }
}
