package shopify.api.core.modulos.resena.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Solicitudes de reseñas (R-011). */
public final class ResenaRequests {

    private ResenaRequests() {
    }

    /** Crear o editar: calificación entera de 1 a 5 y comentario opcional de hasta 1000 caracteres. */
    public record ResenaRequest(
            @NotNull(message = "La calificación es obligatoria")
            @Min(value = 1, message = "La calificación va de 1 a 5")
            @Max(value = 5, message = "La calificación va de 1 a 5")
            Integer calificacion,
            @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
            String comentario) {
    }

    public record OcultarResenaRequest(
            @NotBlank(message = "Indica el motivo") @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
            String motivo) {
    }
}
