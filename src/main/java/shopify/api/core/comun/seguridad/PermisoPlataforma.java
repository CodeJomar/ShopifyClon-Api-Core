package shopify.api.core.comun.seguridad;

import java.util.Arrays;
import java.util.List;

/**
 * Catálogo de permisos de la plataforma (R-001). Hoy todo administrador de plataforma los tiene todos; con roles
 * de plataforma (B9) cada administrador tendrá solo los de su rol. El catálogo se publica ya para que el frontend
 * pueda ocultar secciones por permiso sin depender de la bandera de administrador.
 */
public enum PermisoPlataforma {
    PLATAFORMA_TIENDAS,
    PLATAFORMA_SUSPENDER_TIENDAS,
    PLATAFORMA_USUARIOS,
    PLATAFORMA_PLANES,
    PLATAFORMA_PEDIDOS,
    PLATAFORMA_AUDITORIA,
    PLATAFORMA_SEGURIDAD,
    PLATAFORMA_ADMINISTRADORES;

    /** Nombres de todos los permisos, en orden de declaración. */
    public static List<String> nombresDeTodos() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
