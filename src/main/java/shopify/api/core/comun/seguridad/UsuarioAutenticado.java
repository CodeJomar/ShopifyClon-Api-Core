package shopify.api.core.comun.seguridad;

import java.util.UUID;

/** Principal de la sesión: usuario verificado contra la base en cada petición. */
public record UsuarioAutenticado(UUID id, String correo, boolean esAdministradorPlataforma) {
}
