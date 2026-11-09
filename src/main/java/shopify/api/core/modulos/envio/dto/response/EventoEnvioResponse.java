package shopify.api.core.modulos.envio.dto.response;

import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.envio.modelo.EstadoEventoEnvio;

/** Un evento del historial del envío (más reciente primero en los listados). */
public record EventoEnvioResponse(UUID id, EstadoEventoEnvio estado, String nota, String fotoUrl, Instant fechaEvento) {
}
