package shopify.api.core.modulos.pago.modelo;

import java.util.UUID;

/** Unidades de una variante que el reembolso vuelve al stock (INV-006). */
public record LineaReposicion(UUID varianteId, int cantidad) {
}
