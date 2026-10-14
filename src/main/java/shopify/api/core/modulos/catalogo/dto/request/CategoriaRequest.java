package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code identificador_url} ausente se genera desde el nombre (CAT-002); presente se valida y debe ser único. */
public record CategoriaRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 100, message = "El identificador no puede superar los 100 caracteres")
        String identificadorUrl,

        @Size(max = 2000, message = "La descripción no puede superar los 2000 caracteres")
        String descripcion) {
}
