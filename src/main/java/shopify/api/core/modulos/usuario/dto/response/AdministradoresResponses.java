package shopify.api.core.modulos.usuario.dto.response;

import java.util.List;
import java.util.UUID;

/** Respuestas de la gestión de roles y administradores de plataforma (R-001, B9). */
public final class AdministradoresResponses {

    private AdministradoresResponses() {
    }

    public record PermisoPlataformaResponse(String codigo, String descripcion) {
    }

    public record RolPlataformaResponse(
            UUID id,
            String codigo,
            String nombre,
            String descripcion,
            boolean esSistema,
            List<String> permisos,
            long administradores) {
    }

    public record RolResumen(UUID id, String codigo, String nombre) {
    }

    public record AdministradorResponse(
            UUID usuarioId,
            String correo,
            String nombre,
            boolean esSuperadministrador,
            RolResumen rol,
            List<String> permisos) {
    }
}
