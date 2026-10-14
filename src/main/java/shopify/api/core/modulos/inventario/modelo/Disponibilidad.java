package shopify.api.core.modulos.inventario.modelo;

/**
 * Disponibilidad de una variante para vender (INV-001): {@code vendible = Disponible − Reservado}.
 * Está disponible si no rastrea inventario, si permite vender sin stock, o si queda stock vendible.
 */
public record Disponibilidad(int vendible, boolean rastrea, boolean ventaSinStock, boolean disponible) {
}
