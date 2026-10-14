package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;

/** Línea de una orden tal como quedó guardada. */
public record ItemOrdenResumen(
        String tituloProducto,
        String tituloVariante,
        String sku,
        BigDecimal precioUnitario,
        int cantidad,
        BigDecimal totalLinea) {
}
