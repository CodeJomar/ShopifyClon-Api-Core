package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Reemplaza el conjunto de categorías de un producto (CAT-009). */
public record CategoriasRequest(@NotNull(message = "Indica las categorías") List<UUID> categoriaIds) {
}
