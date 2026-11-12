package shopify.api.core.modulos.usuario.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PerfilResponse(
        UUID id,
        String correoElectronico,
        String nombres,
        String apellidos,
        String telefono,
        boolean esAdministradorPlataforma,
        boolean dosPasosActivo,
        Instant fechaCreacion,
        /** R-001: qué es la persona (comprador, propietario, administrador) y qué puede hacer en cada tienda. */
        AccesoResponse acceso) {

    public PerfilResponse conAcceso(AccesoResponse nuevoAcceso) {
        // Quien administra con un rol de plataforma también ve el panel de administración
        return new PerfilResponse(id, correoElectronico, nombres, apellidos, telefono,
                esAdministradorPlataforma || nuevoAcceso.esAdministradorPlataforma(), dosPasosActivo, fechaCreacion,
                nuevoAcceso);
    }
}
