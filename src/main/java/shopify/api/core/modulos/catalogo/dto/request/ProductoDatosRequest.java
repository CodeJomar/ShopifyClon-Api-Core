package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos del producto sin sus variantes (esas se editan por separado). */
public record ProductoDatosRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String titulo,

        @Size(max = 200, message = "El identificador no puede superar los 200 caracteres")
        String identificadorUrl,

        @Size(max = 5000, message = "La descripción no puede superar los 5000 caracteres")
        String descripcion,

        @Size(max = 100, message = "El proveedor no puede superar los 100 caracteres")
        String proveedor,

        Boolean esDigital) {
}
