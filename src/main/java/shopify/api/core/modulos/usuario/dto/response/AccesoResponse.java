package shopify.api.core.modulos.usuario.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Qué es la persona y qué puede hacer (R-001). Todo usuario es comprador; propietario es quien tiene una tienda como
 * dueño (derivado, no se guarda); administrador de plataforma es una bandera aparte. Una misma persona puede ser varias
 * cosas a la vez. Los permisos son informativos para que la web oculte lo que no corresponde: la API valida cada petición.
 */
public record AccesoResponse(
        boolean esComprador,
        boolean esPropietario,
        boolean esAdministradorPlataforma,
        List<String> permisosPlataforma,
        List<TiendaAccesoResponse> tiendas) {

    /** Una tienda de la persona con su rol y los permisos que ese rol le da en ella. */
    public record TiendaAccesoResponse(
            UUID tiendaId,
            String nombre,
            String subdominio,
            String rol,
            boolean esPropietarioDeLaTienda,
            List<String> permisos) {
    }
}
