package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambiarRolRequest(
        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = ReglasPersonal.PATRON_ROL, message = ReglasPersonal.MENSAJE_ROL)
        String rol,

        @NotBlank(message = ReglasPersonal.MENSAJE_MOTIVO)
        @Size(min = 5, max = 255, message = ReglasPersonal.MENSAJE_MOTIVO)
        String motivo) {
}
