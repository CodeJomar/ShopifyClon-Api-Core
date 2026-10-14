package shopify.api.core.modulos.usuario.dto.response;

import java.util.UUID;

public record TiendaDeUsuarioResponse(UUID tiendaId, String nombre, String subdominio, String rol) {
}
