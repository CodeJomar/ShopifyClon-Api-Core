package shopify.api.core.modulos.catalogo.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Respuestas públicas de la vitrina. Nunca incluyen {@code costo_unitario} ni cantidades exactas (CAT-011, CAT-012).
 */
public final class VitrinaResponses {

    private VitrinaResponses() {
    }

    public record Categoria(String nombre, String identificadorUrl) {
    }

    /**
     * Precio mínimo, comparación y disponibilidad son los de la variante más barata. Con campaña vigente (R-028) el
     * precio ya es el final y la comparación es el precio de lista.
     */
    public record ProductoResumen(
            String titulo,
            String identificadorUrl,
            BigDecimal precioMinimo,
            BigDecimal precioComparacion,
            String imagenPrincipal,
            boolean disponible,
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

    public record ProductoDetalle(
            String titulo,
            String identificadorUrl,
            String descripcion,
            List<Categoria> categorias,
            List<Variante> variantes,
            List<Imagen> imagenes,
            BigDecimal calificacionPromedio,
            long cantidadResenas) {
    }

    public record Variante(
            UUID id,
            String titulo,
            BigDecimal precio,
            BigDecimal precioComparacion,
            boolean disponible,
            boolean requiereEnvio,
            Integer descuentoCampanaPorcentaje,
            String campana) {
    }

    public record Imagen(String urlImagen, String textoAlternativo, UUID varianteId, Integer posicion) {
    }
}
