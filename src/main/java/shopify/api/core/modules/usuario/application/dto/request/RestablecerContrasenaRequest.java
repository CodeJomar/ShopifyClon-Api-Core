package shopify.api.core.modules.usuario.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RestablecerContrasenaRequest {

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El formato de correo no es válido")
    private String correoElectronico;

    @NotBlank(message = "El código de verificación es obligatorio")
    @Size(min = 6, max = 6, message = "El código debe ser de 6 dígitos")
    private String codigo;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
    private String nuevaContrasena;
}