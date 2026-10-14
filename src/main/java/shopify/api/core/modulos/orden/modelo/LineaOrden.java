package shopify.api.core.modulos.orden.modelo;

import java.util.UUID;

/** Línea de una orden para operaciones de inventario: variante, cantidad y si requiere envío. */
public record LineaOrden(UUID varianteId, int cantidad, boolean requiereEnvio) {
}
