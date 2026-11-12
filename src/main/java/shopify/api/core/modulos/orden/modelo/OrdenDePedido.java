package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Orden hija de un pedido, con lo que necesita la lectura del comprador. */
public record OrdenDePedido(
        UUID ordenId,
        String numeroOrden,
        UUID tiendaId,
        EstadoOrden estado,
        EstadoPago estadoPago,
        EstadoPreparacion estadoPreparacion,
        BigDecimal total,
        String tokenAcceso,
        List<ItemOrdenResumen> items) {
}
