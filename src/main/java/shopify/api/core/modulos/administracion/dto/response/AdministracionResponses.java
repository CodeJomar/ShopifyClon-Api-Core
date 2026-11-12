package shopify.api.core.modulos.administracion.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Respuestas de la administración de plataforma (R-001, B8). Solo las ve un administrador de plataforma. */
public final class AdministracionResponses {

    private AdministracionResponses() {
    }

    public record Duenio(UUID id, String correo, String nombre) {
    }

    public record TiendaAdmin(
            UUID id,
            String nombre,
            String subdominio,
            boolean estaActiva,
            String moneda,
            Duenio duenio,
            String codigoPlan,
            String estadoSuscripcion,
            BigDecimal ventasHistoricas,
            int ordenesPagadas,
            Instant fechaCreacion) {
    }

    public record MiembroEquipo(UUID usuarioId, String correo, String nombre, String rol) {
    }

    public record TiendaAdminDetalle(
            TiendaAdmin tienda,
            String correoContacto,
            BigDecimal reembolsado,
            BigDecimal comisiones,
            BigDecimal saldoPendiente,
            Instant finPeriodoActual,
            List<MiembroEquipo> equipo) {
    }

    public record UsuarioAdmin(
            UUID id,
            String correo,
            String nombres,
            String apellidos,
            boolean estaActivo,
            boolean bloqueado,
            Instant bloqueadoHasta,
            boolean dosPasosActivo,
            boolean esAdministradorPlataforma,
            int tiendasPropias,
            Instant fechaCreacion) {
    }

    public record TiendaDeUsuario(UUID tiendaId, String nombre, String subdominio, String rol) {
    }

    public record UsuarioAdminDetalle(UsuarioAdmin usuario, String telefono, List<TiendaDeUsuario> tiendas) {
    }

    public record RegistroAuditoria(
            UUID id,
            String categoria,
            String accion,
            String entidad,
            UUID entidadId,
            UUID usuarioId,
            UUID tiendaId,
            String motivo,
            Map<String, Object> detalle,
            Instant fecha) {
    }

    public record PedidoAdmin(
            UUID id,
            String numeroPedido,
            String correoCliente,
            String moneda,
            BigDecimal total,
            String pasarela,
            int ordenes,
            int ordenesPagadas,
            Instant fechaCreacion) {
    }
}
