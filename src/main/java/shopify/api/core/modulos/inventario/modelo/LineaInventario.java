package shopify.api.core.modulos.inventario.modelo;

import java.util.UUID;

/** Una variante y la cantidad (mayor que 0) de una operación de inventario. */
public record LineaInventario(UUID varianteId, int cantidad) {
}
