package shopify.api.core.modulos.envio.evento;

import java.util.UUID;

/** Publicado tras despachar un envío. Nadie escucha todavía (correo al comprador con la guía, futuro). */
public record EnvioDespachadoEvento(UUID tiendaId, UUID envioId, UUID ordenId) {
}
