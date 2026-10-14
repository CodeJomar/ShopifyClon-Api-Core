package shopify.api.core.modulos.orden.eventos;

import java.util.UUID;

/** Publicada dentro de la confirmación de pago (PAG-005). Los efectos externos escucharán después del commit. */
public record OrdenPagadaEvento(UUID tiendaId, UUID ordenId) {
}
