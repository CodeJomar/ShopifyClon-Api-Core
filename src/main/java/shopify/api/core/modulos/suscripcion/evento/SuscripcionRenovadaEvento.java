package shopify.api.core.modulos.suscripcion.evento;

import java.util.UUID;

/** Publicado tras commit cuando un cobro de renovación se aprueba y abre el período nuevo (SUS-005). */
public record SuscripcionRenovadaEvento(UUID tiendaId) {
}
