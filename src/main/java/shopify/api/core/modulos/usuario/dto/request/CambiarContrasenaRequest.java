package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 200, message = "La contraseña es demasiado larga")
        String contrasenaActual,

        @NotBlank(message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Size(min = 8, max = 72, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        @Pattern(regexp = ValidacionesUsuario.PATRON_CONTRASENA, message = ValidacionesUsuario.MENSAJE_CONTRASENA)
        String contrasenaNueva) {
}
