package shopify.api.core.modulos.mercado.modelo;

/** Tienda visible en el mercado con la cantidad de productos que muestra. */
public record TiendaMercado(
        String nombre,
        String subdominio,
        String descripcion,
        String logoUrl,
        String moneda,
        long cantidadProductos,
        String paleta,
        String tipografia,
        String tamanoLetra,
        String diseno,
        String modo) {
}
