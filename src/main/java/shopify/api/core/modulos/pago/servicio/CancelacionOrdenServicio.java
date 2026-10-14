package shopify.api.core.modulos.pago.servicio;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.comun.auditoria.AccionAuditoria;
import shopify.api.core.comun.auditoria.AuditoriaServicio;
import shopify.api.core.comun.evento.PublicadorEvento;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.cupon.servicio.CuponServicio;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.LineaOrden;
import shopify.api.core.modulos.orden.modelo.OrdenEstado;
import shopify.api.core.modulos.orden.servicio.OrdenServicio;
import shopify.api.core.modulos.pago.eventos.OrdenCanceladaEvento;

/**
 * ORD-007 y ORD-008: cancelación de órdenes sin pago. Libera la reserva, devuelve el uso del cupón, anula la orden y
 * audita. El orden de bloqueo es orden → inventario → cupón, igual que en el checkout.
 */
@Service
@RequiredArgsConstructor
public class CancelacionOrdenServicio {

    public static final String MOTIVO_PAGO_VENCIDO = "PAGO_VENCIDO";

    private final OrdenServicio ordenes;
    private final InventarioServicio inventario;
    private final CuponServicio cupones;
    private final AuditoriaServicio auditoria;
    private final PublicadorEvento publicador;

    /** ORD-007 desde el panel: el personal cancela una orden no pagada. Motivo OTRO exige texto. */
    @Transactional
    public void cancelarPorPersonal(UUID tiendaId, UUID ordenId, String motivo, String detalle) {
        if ("OTRO".equals(motivo) && (detalle == null || detalle.isBlank())) {
            throw new ReglaNegocioException("VAL-001", "Describe el motivo de la cancelación");
        }
        anularSinPago(tiendaId, ordenId, motivo, detalle);
    }

    /** ORD-007/008: anula una orden sin pago. Lo usan el personal y el programador de vencimientos. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void anularSinPago(UUID tiendaId, UUID ordenId, String motivo, String detalle) {
        OrdenEstado orden = ordenes.bloquear(tiendaId, ordenId);
        if (orden.estadoPago().estaCobrado()) {
            throw new ConflictoEstadoException("ORD-007", "Para cancelar una orden pagada emite un reembolso");
        }
        if (orden.estado() != EstadoOrden.ABIERTA || orden.estadoPreparacion() != EstadoPreparacion.NO_PREPARADO) {
            throw new ConflictoEstadoException("ORD-007", "Solo se cancela una orden sin pagar y sin preparar");
        }

        List<LineaInventario> lineas = orden.items().stream()
                .map(linea -> new LineaInventario(linea.varianteId(), linea.cantidad()))
                .toList();
        inventario.liberar(tiendaId, lineas, ordenId);
        if (orden.cuponId() != null) {
            cupones.devolverUso(orden.cuponId());
        }
        ordenes.anular(ordenId, motivo, detalle);

        auditoria.registrar(tiendaId, AccionAuditoria.ORDEN_CANCELADA, "Ordenes", ordenId, motivo,
                Map.of("motivo", motivo, "detalle", detalle == null ? "" : detalle));
        publicador.publicar(new OrdenCanceladaEvento(tiendaId, ordenId, motivo));
    }
}
