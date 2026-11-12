package shopify.api.core.modulos.orden.modelo;

import java.util.List;

/** Estado de pago del pedido, derivado de sus órdenes hijas (las canceladas no cuentan). */
public enum EstadoPagoPedido {
    PENDIENTE,
    PARCIALMENTE_PAGADO,
    PAGADO,
    CANCELADO;

    public static EstadoPagoPedido derivar(List<OrdenDePedido> ordenes) {
        List<OrdenDePedido> vigentes = ordenes.stream().filter(o -> o.estado() != EstadoOrden.CANCELADA).toList();
        if (vigentes.isEmpty()) {
            return CANCELADO;
        }
        long cobradas = vigentes.stream().filter(o -> o.estadoPago().estaCobrado()).count();
        if (cobradas == 0) {
            return PENDIENTE;
        }
        return cobradas == vigentes.size() ? PAGADO : PARCIALMENTE_PAGADO;
    }
}
