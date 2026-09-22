package shopify.api.core.modules.usuario.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginUsuarioRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo no es válido")
    private String correoElectronico;

    @NotBlank(message = "La contraseña es obligatoria")
    private String contrasena;
}