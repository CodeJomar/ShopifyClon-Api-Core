package shopify.api.core.modulos.pago.servicio;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.dto.response.PagoResponse;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;
import shopify.api.core.modulos.pago.modelo.TipoPago;
import shopify.api.core.modulos.pago.repositorio.PagoRepositorio;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

/** Pagos de una orden: registro manual (PAG-004) y consulta del historial. */
@Service
@RequiredArgsConstructor
public class PagoServicio {

    private static final String MOTIVO_MANUAL = "Pago manual";

    private final PagoRepositorio pagos;
    private final OrdenServicio ordenes;
    private final ConfirmacionPagoServicio confirmacion;
    private final TiendaServicio tiendas;
    private final AuditoriaServicio auditoria;

    /**
     * PAG-004: el personal registra un cargo aprobado por el total de la orden. Solo si la orden está ABIERTA y el pago
     * PENDIENTE; una orden vencida pero aún PENDIENTE sí se puede pagar. Luego se confirma el pago (PAG-005).
     */
    @Transactional
    public PagoResponse registrarManual(UUID tiendaId, UUID ordenId, String referencia) {
        OrdenEstado orden = ordenes.bloquear(tiendaId, ordenId);
        if (orden.estado() != EstadoOrden.ABIERTA || orden.estadoPago() != EstadoPago.PENDIENTE
                || orden.total().signum() <= 0) {
            throw new ConflictoEstadoException("PAG-004", "Esta orden no admite un pago manual");
        }

        PagoEntidad pago = new PagoEntidad();
        pago.setOrdenId(orden.id());
        pago.setPasarelaPago(PasarelaPago.MANUAL);
        pago.setMonto(orden.total());
        pago.setMoneda(tiendas.configuracionVenta(tiendaId).moneda());
        pago.setEstado(EstadoRegistroPago.APROBADO);
        pago.setTipo(TipoPago.CARGO);
        pago.setReferencia(referenciaLimpia(referencia));
        pagos.saveAndFlush(pago);

        confirmacion.confirmar(tiendaId, pago.getId());
        auditoria.registrar(tiendaId, AccionAuditoria.PAGO_MANUAL_REGISTRADO, "Pagos", pago.getId(), MOTIVO_MANUAL,
                Map.of("orden_id", orden.id().toString(), "monto", orden.total().toPlainString()));
        return respuesta(pago);
    }

    /** Pagos y reembolsos de la orden, sin datos de la respuesta de la pasarela. ORD-404 si no es de la tienda. */
    @Transactional(readOnly = true)
    public List<PagoResponse> listar(UUID tiendaId, UUID ordenId) {
        ordenes.exigirDeTienda(tiendaId, ordenId);
        return pagos.findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(ordenId).stream()
                .map(PagoServicio::respuesta)
                .toList();
    }

    private static String referenciaLimpia(String referencia) {
        return referencia == null || referencia.isBlank() ? null : referencia.strip();
    }

    private static PagoResponse respuesta(PagoEntidad pago) {
        return new PagoResponse(pago.getId(), pago.getTipo(), pago.getPasarelaPago(), pago.getEstado(), pago.getMonto(),
                pago.getMoneda(), pago.getReferencia(), pago.getMotivo(), pago.getPagoOrigenId(),
                pago.getFechaCreacion());
    }
}
