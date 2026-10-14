package shopify.api.core.modulos.catalogo.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Detalle del panel: incluye costo unitario e inventario (CAT-012). */
public record ProductoDetalleResponse(
        UUID id,
        String titulo,
        String identificadorUrl,
        String descripcion,
        String proveedor,
        Boolean esDigital,
        Boolean estaPublicado,
        List<CategoriaRef> categorias,
        List<Variante> variantes,
        List<Imagen> imagenes) {

    public record CategoriaRef(UUID id, String nombre, String identificadorUrl) {
    }

    public record Inventario(int disponible, int reservada, boolean rastreaInventario, boolean permitirVentaSinStock) {
    }

    public record Variante(
            UUID id,
            String titulo,
            String sku,
            String codigoBarras,
            BigDecimal precio,
            BigDecimal precioComparacion,
            BigDecimal costoUnitario,
            BigDecimal pesoGramos,
            Boolean requiereEnvio,
            Inventario inventario) {
    }

    public record Imagen(
            UUID id,
            String urlImagen,
            String textoAlternativo,
            UUID varianteId,
            Integer posicion,
            Boolean esPrincipal) {
    }
}
