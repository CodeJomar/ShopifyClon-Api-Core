package shopify.api.core.modulos.pago.eventos;

import java.util.UUID;

/** Publicada tras cancelar una orden sin pago. Nadie escucha todavía (correo y aviso a la tienda, futuro). */
public record OrdenCanceladaEvento(UUID tiendaId, UUID ordenId, String motivo) {
}
