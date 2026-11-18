package shopify.api.core.modulos.resena.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respuestas públicas de las reseñas (R-011). Nunca incluyen el correo de quien reseña. */
public final class ResenaResponses {

    private ResenaResponses() {
    }

    /** {@code autor} es el nombre y la inicial del apellido ("Ana P."). */
    public record Resena(UUID id, int calificacion, String comentario, String autor, boolean compraVerificada, Instant fecha) {
    }

    /** {@code promedio} con un decimal; nulo si todavía no hay reseñas. {@code distribucion} va de "1" a "5". */
    public record Resumen(BigDecimal promedio, long total, Map<String, Long> distribucion) {
    }

    public record PaginaResenas(List<Resena> datos, long total, int paginaActual, int totalPaginas) {
    }

    /** {@code miResena} y {@code puedoResenar} dependen de la sesión: sin ella son nulo y falso. */
    public record ResenasProducto(Resumen resumen, Resena miResena, boolean puedoResenar, PaginaResenas pagina) {
    }

    /** Lo que muestran las tarjetas y el detalle: promedio con un decimal (nulo sin reseñas) y cantidad. */
    public record Calificacion(BigDecimal promedio, long cantidad) {
    }
}
