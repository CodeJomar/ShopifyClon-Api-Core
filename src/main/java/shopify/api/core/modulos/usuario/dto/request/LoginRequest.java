package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoElectronico,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 200, message = "La contraseña es demasiado larga")
        String contrasena) {
}
