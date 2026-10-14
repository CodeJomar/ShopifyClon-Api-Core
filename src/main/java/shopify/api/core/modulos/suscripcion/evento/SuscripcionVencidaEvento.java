package shopify.api.core.modulos.suscripcion.evento;

import java.util.UUID;

/** Publicado tras commit cuando la suscripción de una tienda pasa a VENCIDA (SUS-005, SUS-006). */
public record SuscripcionVencidaEvento(UUID tiendaId) {
}
