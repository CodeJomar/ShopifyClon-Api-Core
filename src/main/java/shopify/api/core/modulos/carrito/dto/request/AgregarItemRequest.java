package shopify.api.core.modulos.carrito.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AgregarItemRequest(
        @NotNull(message = "Indica la variante") UUID varianteId,

        @NotNull(message = "Indica la cantidad")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 99, message = "La cantidad máxima por producto es 99")
        Integer cantidad) {
}
