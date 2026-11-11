package shopify.api.core.modulos.usuario.servicio;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import shopify.api.core.comun.excepcion.DemasiadasPeticionesException;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.comun.seguridad.UsuarioSesionRepositorio;
import shopify.api.core.comun.util.GeneradorToken;
import shopify.api.core.comun.websocket.ValidadorTicketWs;
import shopify.api.core.modulos.usuario.dto.response.TicketWebSocketResponse;

/**
 * Emite y consume tickets WebSocket de un solo uso con vida corta (sin tocar la base de datos).
 * Permite a clientes web tras proxy con cookie {@code httpOnly} abrir la conexión STOMP sin exponer el JWT.
 */
@Service
public class TicketWebSocketServicio implements ValidadorTicketWs {

    private static final int MAX_TICKETS_ACTIVOS = 10_000;

    private final GeneradorToken generadorToken;
    private final UsuarioSesionRepositorio usuarioSesionRepositorio;
    private final Clock reloj;
    private final int ttlSegundos;
    private final ConcurrentHashMap<String, TicketRegistro> tickets = new ConcurrentHashMap<>();

    public TicketWebSocketServicio(
            GeneradorToken generadorToken,
            UsuarioSesionRepositorio usuarioSesionRepositorio,
            Clock reloj,
            @Value("${app.websocket.ticket-ttl-segundos:60}") int ttlSegundos) {
        if (ttlSegundos < 1) {
            throw new IllegalArgumentException("app.websocket.ticket-ttl-segundos debe ser al menos 1");
        }
        this.generadorToken = generadorToken;
        this.usuarioSesionRepositorio = usuarioSesionRepositorio;
        this.reloj = reloj;
        this.ttlSegundos = ttlSegundos;
    }

    /** Emite un ticket efímero asociado al usuario autenticado de la petición actual. */
    public TicketWebSocketResponse emitirParaUsuarioActual() {
        UUID usuarioId = SesionActual.usuarioId();
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MILLIS);
        depurarExpirados(ahora);
        if (tickets.size() >= MAX_TICKETS_ACTIVOS) {
            throw new DemasiadasPeticionesException(5);
        }

        String ticket = generadorToken.tokenUrl();
        Instant expiraEn = ahora.plusSeconds(ttlSegundos);
        tickets.put(GeneradorToken.sha256Hex(ticket), new TicketRegistro(usuarioId, ahora, expiraEn));
        return new TicketWebSocketResponse(ticket, expiraEn, ttlSegundos);
    }

    @Override
    public Optional<SesionTicketWs> validarYConsumir(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return Optional.empty();
        }
        TicketRegistro registro = tickets.remove(GeneradorToken.sha256Hex(ticket.strip()));
        Instant ahora = reloj.instant();
        if (registro == null || !registro.expiraEn().isAfter(ahora)) {
            return Optional.empty();
        }
        return usuarioSesionRepositorio.buscarActivoPorId(registro.usuarioId(), registro.emitidoEn())
                .map(usuario -> new SesionTicketWs(usuario, registro.emitidoEn()));
    }

    private void depurarExpirados(Instant ahora) {
        tickets.entrySet().removeIf(entrada -> !entrada.getValue().expiraEn().isAfter(ahora));
    }

    private record TicketRegistro(UUID usuarioId, Instant emitidoEn, Instant expiraEn) {
    }
}
