package shopify.api.core.modulos.carrito.modelo;

import java.util.UUID;

/** Variante y cantidad de una línea del carrito, sin precios: el checkout los toma del catálogo al momento. */
public record LineaCarrito(UUID varianteId, int cantidad) {
}
