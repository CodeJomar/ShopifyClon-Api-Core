package shopify.api.core.modulos.usuario.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PerfilResponse(
        UUID id,
        String correoElectronico,
        String nombres,
        String apellidos,
        String telefono,
        boolean esAdministradorPlataforma,
        Instant fechaCreacion) {
}
