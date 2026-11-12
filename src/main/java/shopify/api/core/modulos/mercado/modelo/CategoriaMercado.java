package shopify.api.core.modulos.mercado.modelo;

/** Categoría agregada entre tiendas por su identificador (las categorías son de cada tienda). */
public record CategoriaMercado(String nombre, String slug, long cantidadProductos) {
}
