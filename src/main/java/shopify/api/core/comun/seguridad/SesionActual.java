package shopify.api.core.comun.seguridad;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import shopify.api.core.comun.excepcion.NoAutenticadoException;

/** Acceso a la sesión del usuario actual (lee el {@code SecurityContext}). */
public final class SesionActual {

    private SesionActual() {
    }

    public static UUID usuarioId() {
        return usuarioIdOpcional()
                .orElseThrow(() -> new NoAutenticadoException("SEG-007", "Inicia sesión para continuar"));
    }

    public static Optional<UUID> usuarioIdOpcional() {
        return usuario().map(UsuarioAutenticado::id);
    }

    public static Optional<UsuarioAutenticado> usuario() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.isAuthenticated()
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public static boolean esAdministradorPlataforma() {
        return usuario().map(UsuarioAutenticado::esAdministradorPlataforma).orElse(false);
    }
}
