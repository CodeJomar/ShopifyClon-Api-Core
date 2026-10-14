package shopify.api.core.modulos.tienda.dto.response;

import java.util.UUID;

public record MiTiendaResponse(
        UUID tiendaId,
        String nombre,
        String subdominio,
        String rol,
        boolean estaActiva,
        boolean operativa) {
}
