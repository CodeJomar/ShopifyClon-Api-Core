package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RestablecerClaveRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoElectronico,

        @NotBlank(message = ValidacionesUsuario.MENSAJE_CODIGO)
        @Pattern(regexp = ValidacionesUsuario.PATRON_CODIGO, message = ValidacionesUsuario.MENSAJE_CODIGO)
        String codigo,

        @NotBlank(message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Size(min = 8, max = 72, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Pattern(regexp = ValidacionesUsuario.PATRON_CONTRASENA, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        String contrasenaNueva) {
}
