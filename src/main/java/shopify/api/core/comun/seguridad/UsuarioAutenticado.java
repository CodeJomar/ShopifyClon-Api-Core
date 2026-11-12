package shopify.api.core.comun.seguridad;

import java.util.Set;
import java.util.UUID;

/**
 * Principal de la sesión: usuario verificado contra la base en cada petición. {@code esAdministradorPlataforma} es la
 * bandera de superadministrador (todos los permisos); {@code permisosPlataforma} son los efectivos: todos para el
 * superadministrador, los de su rol para un administrador con rol, ninguno para el resto.
 */
public record UsuarioAutenticado(UUID id, String correo, boolean esAdministradorPlataforma,
                                 Set<PermisoPlataforma> permisosPlataforma) {

    public UsuarioAutenticado {
        permisosPlataforma = esAdministradorPlataforma ? Set.of(PermisoPlataforma.values()) : Set.copyOf(permisosPlataforma);
    }

    public UsuarioAutenticado(UUID id, String correo, boolean esAdministradorPlataforma) {
        this(id, correo, esAdministradorPlataforma, Set.of());
    }

    /** Administra la plataforma: superadministrador o con un rol que le da al menos un permiso. */
    public boolean administraPlataforma() {
        return esAdministradorPlataforma || !permisosPlataforma.isEmpty();
    }

    public boolean tiene(PermisoPlataforma permiso) {
        return permisosPlataforma.contains(permiso);
    }
}
