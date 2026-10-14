package shopify.api.core.modulos.inventario.modelo;

/** Cantidades crudas de una variante, para el panel (la vitrina solo ve {@link Disponibilidad}). */
public record Cantidades(int disponible, int reservada, boolean rastrea, boolean ventaSinStock) {
}
