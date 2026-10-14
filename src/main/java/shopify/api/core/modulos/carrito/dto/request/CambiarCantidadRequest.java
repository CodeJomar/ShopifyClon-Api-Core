package shopify.api.core.modulos.carrito.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Cantidad 0 quita la línea (CAR-003). */
public record CambiarCantidadRequest(
        @NotNull(message = "Indica la cantidad")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        @Max(value = 99, message = "La cantidad máxima por producto es 99")
        Integer cantidad) {
}
