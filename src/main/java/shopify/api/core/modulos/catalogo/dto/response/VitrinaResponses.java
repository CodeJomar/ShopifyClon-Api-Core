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

    /** Precio mínimo, comparación y disponibilidad son los de la variante más barata. */
    public record ProductoResumen(
            String titulo,
            String identificadorUrl,
            BigDecimal precioMinimo,
            BigDecimal precioComparacion,
            String imagenPrincipal,
            boolean disponible) {
    }

    public record ProductoDetalle(
            String titulo,
            String identificadorUrl,
            String descripcion,
            List<Categoria> categorias,
            List<Variante> variantes,
            List<Imagen> imagenes) {
    }

    public record Variante(
            UUID id,
            String titulo,
            BigDecimal precio,
            BigDecimal precioComparacion,
            boolean disponible,
            boolean requiereEnvio) {
    }

    public record Imagen(String urlImagen, String textoAlternativo, UUID varianteId, Integer posicion) {
    }
}
