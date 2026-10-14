package shopify.api.core.comun.seguridad;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static shopify.api.core.comun.seguridad.Permiso.*;

/** Roles de una tienda con su matriz de permisos fija en código (D-006). */
public enum RolTienda {

    PROPIETARIO(EnumSet.allOf(Permiso.class)),

    ADMINISTRADOR(EnumSet.complementOf(EnumSet.of(SUSCRIPCION_GESTIONAR))),

    GESTOR_CATALOGO(EnumSet.of(CATALOGO_GESTIONAR, INVENTARIO_AJUSTAR)),

    GESTOR_PEDIDOS(EnumSet.of(ORDENES_VER, ORDENES_GESTIONAR, ORDENES_CANCELAR));

    private final Set<Permiso> permisos;

    RolTienda(Set<Permiso> permisos) {
        this.permisos = Set.copyOf(permisos);
    }

    public Set<Permiso> permisos() {
        return permisos;
    }

    public boolean tiene(Permiso permiso) {
        return permisos.contains(permiso);
    }

    /** Un código que no corresponde a un rol de tienda (p. ej. {@code CLIENTE}) devuelve vacío. */
    public static Optional<RolTienda> desdeCodigo(String codigo) {
        if (codigo == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(codigo));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
