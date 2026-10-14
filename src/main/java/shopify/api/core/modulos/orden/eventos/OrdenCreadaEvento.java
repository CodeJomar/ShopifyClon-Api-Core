package shopify.api.core.modulos.orden.eventos;

import java.util.UUID;

/**
 * Publicada dentro de la transacción del checkout (paso 12). Los efectos externos, como el correo de confirmación,
 * escucharán después del commit; hoy no hay oyentes.
 */
public record OrdenCreadaEvento(UUID tiendaId, UUID ordenId, String numeroOrden) {
}
