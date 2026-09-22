package shopify.api.core.shared.seguridad.servicio;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import shopify.api.core.shared.exception.ReglaNegocioException;

import java.util.Optional;
import java.util.UUID;

public final class SeguridadUtil {

    private SeguridadUtil() {
    }

    /**
     * Obtiene el UUID del usuario autenticado actual.
     * Lanza excepción de negocio si no hay sesión activa.
     */
    public static UUID obtenerUsuarioIdActual() {
        return obtenerUsuarioIdOpcional()
                .orElseThrow(() -> new ReglaNegocioException("No se encontró un usuario autenticado en la sesión actual"));
    }

    /**
     * Retorna el UUID opcional si existe (útil en flujos de checkout invitado/anónimo).
     */
    public static Optional<UUID> obtenerUsuarioIdOpcional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(auth.getName()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}