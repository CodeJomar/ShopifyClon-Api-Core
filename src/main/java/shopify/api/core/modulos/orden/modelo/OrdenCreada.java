package shopify.api.core.modulos.orden.modelo;

import java.util.UUID;

/** Resultado de crear una orden: su id, para que quien orquesta enlace el carrito y la reserva. */
public record OrdenCreada(UUID id) {
}
