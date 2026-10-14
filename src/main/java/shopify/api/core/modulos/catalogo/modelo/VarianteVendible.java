package shopify.api.core.modulos.catalogo.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Variante tal como se vende: precio actual y si se puede vender ahora (variante y producto no eliminados,
 * producto publicado, misma tienda). Lo usan el carrito y el checkout (CAR-002, CAR-006).
 */
public record VarianteVendible(
        UUID varianteId,
        UUID productoId,
        String tituloProducto,
        String tituloVariante,
        String sku,
        BigDecimal precio,
        boolean requiereEnvio,
        String urlImagen,
        boolean vendible) {
}
