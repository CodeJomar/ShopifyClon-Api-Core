package shopify.api.core.modulos.pago.servicio;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
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
        PagoEntidad candidato = pagos.findFirstByPasarelaPagoAndTransaccionIdAndEliminadoFalse(
                PasarelaPago.STRIPE, evento.intentoId()).orElse(null);
        if (candidato == null) {
            log.warn("Cobro {} sin pago registrado; se ignora", evento.intentoId());
            return;
        }
        UUID tiendaId = ordenes.tiendaDeOrden(candidato.getOrdenId()).orElse(null);
        if (tiendaId == null) {
            log.warn("Cobro {} de una orden inexistente; se ignora", evento.intentoId());
            return;
        }
        transaccion.executeWithoutResult(estado -> confirmarEnTransaccion(candidato.getId(), tiendaId, evento));
    }

    private void confirmarEnTransaccion(UUID pagoId, UUID tiendaId, EventoPasarela evento) {
        PagoEntidad pago = pagos.bloquearPorId(pagoId).orElse(null);
        if (pago == null || pago.getEstado() != EstadoRegistroPago.PENDIENTE) {
            // Repetido (ya APROBADO) o ya resuelto: no hay efectos
            return;
        }
        OrdenEstado orden = ordenes.bloquear(tiendaId, pago.getOrdenId());
        long esperado = orden.total().movePointRight(2).longValueExact();
        pago.setDetallesRespuesta(detalles(evento));
        if (evento.montoCentavos() != esperado) {
            pago.setEstado(EstadoRegistroPago.RECHAZADO);
            pagos.saveAndFlush(pago);
            log.error("Cobro {} por {} centavos no coincide con el total {}; pago rechazado",
                    evento.intentoId(), evento.montoCentavos(), esperado);
            return;
        }
        pago.setEstado(EstadoRegistroPago.APROBADO);
        pagos.saveAndFlush(pago);
        confirmacion.confirmar(tiendaId, pago.getId());
    }

    private void rechazarCobro(EventoPasarela evento) {
        pagos.findFirstByPasarelaPagoAndTransaccionIdAndEliminadoFalse(PasarelaPago.STRIPE, evento.intentoId())
                .ifPresent(candidato -> transaccion.executeWithoutResult(
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
