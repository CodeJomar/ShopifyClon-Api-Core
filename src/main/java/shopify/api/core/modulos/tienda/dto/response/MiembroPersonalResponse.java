package shopify.api.core.modulos.tienda.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MiembroPersonalResponse(
        UUID usuarioId,
        String correoElectronico,
        String nombres,
        String apellidos,
        String rol,
        Instant desde) {
}
