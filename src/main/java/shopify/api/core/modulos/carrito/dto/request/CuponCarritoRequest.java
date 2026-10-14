package shopify.api.core.modulos.carrito.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CuponCarritoRequest(
        @NotBlank(message = "Indica el código del cupón")
        @Size(max = 50, message = "El código no puede superar los 50 caracteres")
        String codigo) {
}
