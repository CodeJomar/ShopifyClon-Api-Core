package shopify.api.core.comun.websocket;

import java.security.Principal;
import java.time.Instant;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;

/** Principal STOMP que conserva el instante de emisión de las credenciales para revalidar revocación en suscripciones. */
public record UsuarioStompPrincipal(
        UsuarioAutenticado usuario,
        Instant credencialesEmitidasEn) implements Principal {

    @Override
    public String getName() {
        return usuario.id().toString();
    }
}
