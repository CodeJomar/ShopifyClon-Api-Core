package shopify.api.core.modulos.mercado.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Fila del listado de productos del mercado: el producto, su variante más barata y la tienda que lo vende. */
public record ProductoMercado(
        UUID id,
        String titulo,
        String identificadorUrl,
        BigDecimal precioDesde,
        BigDecimal precioComparacionDesde,
        String moneda,
        String imagenUrl,
        boolean esDigital,
        boolean hayStock,
        String tiendaNombre,
        String tiendaSubdominio,
        String tiendaLogoUrl) {
}
