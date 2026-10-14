package shopify.api.core.modulos.catalogo.dto.response;

import java.util.UUID;

public record CategoriaResponse(UUID id, String nombre, String identificadorUrl, String descripcion) {
}
