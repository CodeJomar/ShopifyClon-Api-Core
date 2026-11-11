package shopify.api.core.comun.websocket;

import java.time.Instant;
import java.util.Optional;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;

/** Contrato transversal para validar y consumir tickets efímeros WebSocket sin que {@code comun} dependa de módulos. */
public interface ValidadorTicketWs {

    Optional<SesionTicketWs> validarYConsumir(String ticket);

    record SesionTicketWs(UsuarioAutenticado usuario, Instant sesionEmitidaEn) {
    }
}
