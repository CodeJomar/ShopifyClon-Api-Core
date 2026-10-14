package shopify.api.core.modulos.carrito.modelo;

import java.util.List;
import java.util.UUID;

/** Carrito bloqueado para el checkout: su estado, su cupón, la orden si ya la tiene y sus líneas. */
public record CarritoParaCheckout(
        UUID carritoId,
        EstadoCarrito estado,
        UUID cuponId,
        UUID ordenId,
        List<LineaCarrito> lineas) {
}
