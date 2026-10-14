package shopify.api.core.modulos.inventario.dto.response;

import java.util.UUID;

/** Cantidades y configuración de una variante para el panel. */
public record InventarioVarianteResponse(
        UUID varianteId,
        int disponible,
        int reservada,
        int vendible,
        boolean rastreaInventario,
        boolean permitirVentaSinStock) {
}
