package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ImagenRequest(
        @NotBlank(message = "La URL de la imagen es obligatoria")
        @Size(max = 2000, message = "La URL no puede superar los 2000 caracteres")
        @Pattern(regexp = "^https://.+", message = "La URL de la imagen debe empezar por https://")
        String urlImagen,

        @Size(max = 200, message = "El texto alternativo no puede superar los 200 caracteres")
        String textoAlternativo,

        UUID varianteId,

        @Min(value = 1, message = "La posición empieza en 1")
        Integer posicion,

        Boolean esPrincipal) {
}
