package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Activa o desactiva la verificación en dos pasos; exige la contraseña actual (A-007). */
public record DosPasosRequest(
        @NotNull(message = "Indica si quieres activar o desactivar la verificación en dos pasos")
        Boolean activo,
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 200, message = "La contraseña es demasiado larga")
        String contrasenaActual) {
}
