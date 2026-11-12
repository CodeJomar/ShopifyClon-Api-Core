package shopify.api.core.modulos.orden;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPagoPedido;
import shopify.api.core.modulos.orden.modelo.EstadoPedido;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;
import shopify.api.core.modulos.orden.modelo.OrdenDePedido;

/** El estado del pedido se compone de sus órdenes hijas (no se guarda). */
class EstadoPedidoTest {

    private static OrdenDePedido orden(EstadoOrden estado, EstadoPago pago, EstadoPreparacion preparacion) {
        return new OrdenDePedido(UUID.randomUUID(), "#1001", UUID.randomUUID(), estado, pago, preparacion,
                BigDecimal.TEN, "t", List.of());
    }

    private static OrdenDePedido abierta(EstadoPago pago, EstadoPreparacion preparacion) {
        return orden(EstadoOrden.ABIERTA, pago, preparacion);
    }

    @Test
    void sinPagarEsPendiente() {
        List<OrdenDePedido> hijas = List.of(abierta(EstadoPago.PENDIENTE, EstadoPreparacion.NO_PREPARADO),
                abierta(EstadoPago.PENDIENTE, EstadoPreparacion.NO_PREPARADO));
        assertThat(EstadoPedido.derivar(hijas)).isEqualTo(EstadoPedido.PENDIENTE);
        assertThat(EstadoPagoPedido.derivar(hijas)).isEqualTo(EstadoPagoPedido.PENDIENTE);
    }

    @Test
    void unaPagadaYOtraNoEsParcialmentePagado() {
        List<OrdenDePedido> hijas = List.of(abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO),
                abierta(EstadoPago.PENDIENTE, EstadoPreparacion.NO_PREPARADO));
        assertThat(EstadoPedido.derivar(hijas)).isEqualTo(EstadoPedido.PARCIALMENTE_PAGADO);
    }

    @Test
    void pagadoYLuegoElAvanceDeLaPreparacion() {
        assertThat(EstadoPedido.derivar(List.of(abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO),
                abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO)))).isEqualTo(EstadoPedido.PAGADO);
        assertThat(EstadoPedido.derivar(List.of(abierta(EstadoPago.PAGADO, EstadoPreparacion.PREPARADO),
                abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO)))).isEqualTo(EstadoPedido.PARCIALMENTE_DESPACHADO);
        assertThat(EstadoPedido.derivar(List.of(abierta(EstadoPago.PAGADO, EstadoPreparacion.PREPARADO),
                abierta(EstadoPago.PAGADO, EstadoPreparacion.ENTREGADO)))).isEqualTo(EstadoPedido.DESPACHADO);
        assertThat(EstadoPedido.derivar(List.of(abierta(EstadoPago.PAGADO, EstadoPreparacion.ENTREGADO),
                orden(EstadoOrden.CERRADA, EstadoPago.PAGADO, EstadoPreparacion.ENTREGADO)))).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    void cancelaciones() {
        OrdenDePedido cancelada = orden(EstadoOrden.CANCELADA, EstadoPago.ANULADO, EstadoPreparacion.NO_PREPARADO);
        assertThat(EstadoPedido.derivar(List.of(cancelada))).isEqualTo(EstadoPedido.CANCELADO);
        assertThat(EstadoPedido.derivar(List.of(cancelada, abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO))))
                .isEqualTo(EstadoPedido.CANCELADO_PARCIAL);
        assertThat(EstadoPagoPedido.derivar(List.of(cancelada, abierta(EstadoPago.PAGADO, EstadoPreparacion.NO_PREPARADO))))
                .isEqualTo(EstadoPagoPedido.PAGADO);
    }
}
