package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoElectronico,

        @NotBlank(message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Size(min = 8, max = 72, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Pattern(regexp = ValidacionesUsuario.PATRON_CONTRASENA, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        String contrasena,

        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100, message = "Los nombres no pueden superar los 100 caracteres")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres")
        String apellidos,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        @Pattern(regexp = ValidacionesUsuario.PATRON_TELEFONO, message = ValidacionesUsuario.MENSAJE_TELEFONO)
        String telefono) {
}
