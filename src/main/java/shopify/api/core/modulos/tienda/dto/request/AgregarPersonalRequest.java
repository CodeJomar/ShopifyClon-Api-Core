package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AgregarPersonalRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoElectronico,

        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = ReglasPersonal.PATRON_ROL, message = ReglasPersonal.MENSAJE_ROL)
        String rol) {
}
