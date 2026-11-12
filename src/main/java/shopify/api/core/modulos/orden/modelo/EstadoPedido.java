package shopify.api.core.modulos.orden.modelo;

import java.util.List;

/**
 * Estado del pedido, derivado (no se guarda) de sus órdenes hijas. Las canceladas se reflejan en
 * {@link #CANCELADO_PARCIAL} (si quedan otras vigentes) o {@link #CANCELADO} (si todas lo están).
 * No hay {@code EN_PREPARACION}: la preparación de la tienda es PREPARADO y ya cuenta como despachado.
 */
public enum EstadoPedido {
    PENDIENTE,
    PARCIALMENTE_PAGADO,
    PAGADO,
    PARCIALMENTE_DESPACHADO,
    DESPACHADO,
    ENTREGADO,
    CANCELADO_PARCIAL,
    CANCELADO;

    public static EstadoPedido derivar(List<OrdenDePedido> ordenes) {
        List<OrdenDePedido> vigentes = ordenes.stream().filter(o -> o.estado() != EstadoOrden.CANCELADA).toList();
        if (vigentes.isEmpty()) {
            return CANCELADO;
        }
        if (vigentes.size() < ordenes.size()) {
            return CANCELADO_PARCIAL;
        }
        EstadoPagoPedido pago = EstadoPagoPedido.derivar(ordenes);
        if (pago == EstadoPagoPedido.PENDIENTE) {
            return PENDIENTE;
        }
        if (pago == EstadoPagoPedido.PARCIALMENTE_PAGADO) {
            return PARCIALMENTE_PAGADO;
        }
        long entregadas = vigentes.stream().filter(o -> o.estadoPreparacion() == EstadoPreparacion.ENTREGADO).count();
        long preparadas = vigentes.stream().filter(o -> o.estadoPreparacion() != EstadoPreparacion.NO_PREPARADO).count();
        if (entregadas == vigentes.size()) {
            return ENTREGADO;
        }
        if (preparadas == vigentes.size()) {
            return DESPACHADO;
        }
        return preparadas > 0 ? PARCIALMENTE_DESPACHADO : PAGADO;
    }
}
