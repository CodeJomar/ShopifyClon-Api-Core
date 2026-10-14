package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuitarPersonalRequest(
        @NotBlank(message = ReglasPersonal.MENSAJE_MOTIVO)
        @Size(min = 5, max = 255, message = ReglasPersonal.MENSAJE_MOTIVO)
        String motivo) {
}
