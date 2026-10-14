package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Alta de un producto con sus variantes (1 a 100). Nace no publicado (CAT-007). */
public record ProductoRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String titulo,

        @Size(max = 200, message = "El identificador no puede superar los 200 caracteres")
        String identificadorUrl,

        @Size(max = 5000, message = "La descripción no puede superar los 5000 caracteres")
        String descripcion,

        @Size(max = 100, message = "El proveedor no puede superar los 100 caracteres")
        String proveedor,

        Boolean esDigital,

        List<UUID> categoriaIds,

        @NotNull(message = "Indica al menos una variante")
        @Size(min = 1, max = 100, message = "Un producto tiene de 1 a 100 variantes")
        List<@Valid VarianteRequest> variantes) {
}
