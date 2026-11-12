package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Solicitudes de roles y administradores de plataforma (R-001, B9). */
public final class AdministradoresRequests {

    private AdministradoresRequests() {
    }

    public record RolPlataformaRequest(
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z_]{2,39}$", message = "El código debe tener de 3 a 40 letras mayúsculas o guiones bajos")
            String codigo,
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 255) String descripcion,
            @NotEmpty @Size(max = 20) List<@NotBlank String> permisos) {
    }

    /** Edita nombre, descripción y permisos; el código no cambia. */
    public record EditarRolPlataformaRequest(
            @NotBlank @Size(max = 100) String nombre,
            @Size(max = 255) String descripcion,
            @NotEmpty @Size(max = 20) List<@NotBlank String> permisos) {
    }

    public record NuevoAdministradorRequest(@NotNull UUID usuarioId, @NotNull UUID rolId) {
    }

    public record CambiarRolAdministradorRequest(@NotNull UUID rolId) {
    }
}
