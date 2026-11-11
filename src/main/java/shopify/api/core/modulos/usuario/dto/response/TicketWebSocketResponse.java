package shopify.api.core.modulos.usuario.dto.response;

import java.time.Instant;

/** Ticket efímero de un solo uso para autenticar {@code CONNECT} STOMP sin exponer el JWT principal. */
public record TicketWebSocketResponse(
        String ticket,
        Instant expiraEn,
        int ttlSegundos) {
}
