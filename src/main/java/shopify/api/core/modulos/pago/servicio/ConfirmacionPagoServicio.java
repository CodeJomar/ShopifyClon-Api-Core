package shopify.api.core.modulos.pago.servicio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.eventos.OrdenPagadaEvento;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.repositorio.PagoRepositorio;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;

/**
 * PAG-005: confirmación de pago en una sola transacción. Lo usan el pago manual y, después, el webhook de Stripe.
 * El orden importa: bloquear orden → inventario → comisión → orden PAGADO → balance → evento.
 */
@Service
@RequiredArgsConstructor
public class ConfirmacionPagoServicio {

    private final PagoRepositorio pagos;
    private final OrdenServicio ordenes;
    private final InventarioServicio inventario;
    private final SuscripcionServicio suscripciones;
    private final BalanceServicio balance;
    private final AuditoriaServicio auditoria;
    private final ApplicationEventPublisher eventos;

    /**
     * Confirma un pago aprobado. Si la orden ya está ANULADA (PAG-006), el pago queda APROBADO, la orden no cambia y
     * se audita para reembolso manual. Requiere transacción de quien llama.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmar(UUID tiendaId, UUID pagoId) {
        PagoEntidad pago = pagos.findByIdAndEliminadoFalse(pagoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-404", "No encontramos ese pago"));
        OrdenEstado orden = ordenes.bloquear(tiendaId, pago.getOrdenId());

        if (orden.estadoPago() == EstadoPago.ANULADO) {
            // PAG-006: el dinero llegó tarde; se audita y se deja para reembolso, la orden no se revive
            auditoria.registrar(tiendaId, AccionAuditoria.PAGO_TARDIO_RECIBIDO, "Pagos", pagoId,
                    "Pago recibido para una orden ya anulada", Map.of("orden_id", orden.id().toString()));
            return;
        }

        List<LineaInventario> lineas = orden.items().stream()
                .map(linea -> new LineaInventario(linea.varianteId(), linea.cantidad()))
                .toList();
        inventario.confirmarVenta(tiendaId, lineas, orden.id());

        BigDecimal comision = comisionDe(tiendaId, orden.total());
        ordenes.aplicarPagoConfirmado(orden.id(), comision);
        balance.registrarVenta(tiendaId, orden.total(), comision);
        eventos.publishEvent(new OrdenPagadaEvento(tiendaId, orden.id()));
    }

    /** SUS-012: la comisión es la del plan vigente al aprobarse el pago; sin suscripción vigente, cero. */
    private BigDecimal comisionDe(UUID tiendaId, BigDecimal total) {
        return suscripciones.vigente(tiendaId)
                .map(vigente -> Dinero.porcentaje(total, vigente.porcentajeComision()))
                .orElse(Dinero.CERO);
    }
}
