package shopify.api.core.modulos.tienda.dto.response;

import java.util.UUID;

public record TiendaCreadaResponse(
        UUID id,
        String nombre,
        String subdominio,
        SuscripcionResumenResponse suscripcion,
        boolean operativa) {
}
