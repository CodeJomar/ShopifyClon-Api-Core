package shopify.api.core.modulos.inventario.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Ajuste manual: fija la cantidad disponible y exige un motivo (INV-007, auditoría). */
public record AjusteInventarioRequest(
        @NotNull(message = "Indica la nueva cantidad")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer nuevaCantidad,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(min = 5, max = 255, message = "El motivo debe tener entre 5 y 255 caracteres")
        String motivo) {
}
