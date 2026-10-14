package shopify.api.core.comun.seguridad;

import java.util.UUID;
import shopify.api.core.comun.excepcion.AccesoDenegadoException;

/** Tienda sobre la que opera la petición, ya validada contra la membresía del usuario. */
public record ContextoTienda(UUID tiendaId, UUID usuarioId, RolTienda rol) {

    public boolean tiene(Permiso permiso) {
        return rol.tiene(permiso);
    }

    public void exigir(Permiso permiso) {
        if (!tiene(permiso)) {
            throw new AccesoDenegadoException("SEG-002", "Tu rol no tiene permiso para esta acción");
        }
    }

    public boolean esPropietario() {
        return rol == RolTienda.PROPIETARIO;
    }
}
