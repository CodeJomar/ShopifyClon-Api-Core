package shopify.api.core.modulos.inventario.dto.request;

/** Ambos campos son opcionales: ausente conserva el valor actual. */
public record ConfiguracionInventarioRequest(Boolean rastreaInventario, Boolean permitirVentaSinStock) {
}
