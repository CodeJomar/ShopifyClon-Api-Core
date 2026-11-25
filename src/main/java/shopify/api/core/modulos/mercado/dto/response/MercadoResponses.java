package shopify.api.core.modulos.mercado.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Respuestas públicas del mercado (R-001). Nada de costos, inventario exacto ni datos del propietario. */
public final class MercadoResponses {

    private MercadoResponses() {
    }

    /** Tienda que vende un producto, en versión corta para las tarjetas del listado. */
    public record TiendaResumen(String nombre, String subdominio, String logoUrl) {
    }

    public record ProductoMercadoResponse(
            UUID id,
            String titulo,
            String identificadorUrl,
            BigDecimal precioDesde,
            BigDecimal precioComparacionDesde,
            String moneda,
            String imagenUrl,
            boolean esDigital,
            boolean hayStock,
            TiendaResumen tienda,
            BigDecimal calificacionPromedio,
            long cantidadResenas,
            String descripcionCorta,
            List<VarianteResumen> variantesResumen,
            long cantidadVariantes,
            Integer descuentoCampanaPorcentaje,
            String campana) {
    }

    /** Variante en la tarjeta: su título («Negro / M»), su precio y, con campaña, el precio de lista. */
    public record VarianteResumen(String titulo, BigDecimal precio, BigDecimal precioComparacion) {
    }

    public record TiendaMercadoResponse(
            String nombre,
            String subdominio,
            String descripcion,
            String logoUrl,
            String moneda,
            long cantidadProductos) {
    }

    public record TiendaMercadoDetalleResponse(
            String nombre,
            String subdominio,
            String descripcion,
            String logoUrl,
            String moneda,
            long cantidadProductos,
            List<CategoriaMercadoResponse> categorias) {
    }

    public record CategoriaMercadoResponse(String nombre, String slug, long cantidadProductos) {
    }

    public record ResumenMercado(long tiendas, long productos) {
    }

    public record DestacadosMercadoResponse(
            List<TiendaMercadoResponse> tiendas,
            List<ProductoMercadoResponse> productos,
            ResumenMercado resumen) {
    }
}
