package shopify.api.core.modulos.orden.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Línea de la orden con sus instantáneas (título, SKU, precio) tomadas al momento del checkout. */
public record NuevaLinea(
        UUID varianteId,
        String tituloProducto,
        String tituloVariante,
        String sku,
        int cantidad,
        BigDecimal precioUnitario,
        BigDecimal totalLinea,
        boolean requiereEnvio) {
}
