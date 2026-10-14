package shopify.api.core.modulos.orden.dto.response;

import java.math.BigDecimal;

/** Línea de una orden tal como la ve el comprador o el panel. */
public record ItemOrdenVistaResponse(
        String tituloProducto,
        String tituloVariante,
        String sku,
        BigDecimal precioUnitario,
        int cantidad,
        BigDecimal totalLinea) {
}
