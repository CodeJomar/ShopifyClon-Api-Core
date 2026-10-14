package shopify.api.core.modulos.envio.evento;

import java.util.UUID;

/** Publicado tras entregar un envío. Nadie escucha todavía. */
public record EnvioEntregadoEvento(UUID tiendaId, UUID envioId, UUID ordenId) {
}
