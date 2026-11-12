package shopify.api.core.modulos.administracion.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RequierePermisoPlataforma;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.PedidoAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.RegistroAuditoria;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.TiendaAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.TiendaAdminDetalle;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.UsuarioAdmin;
import shopify.api.core.modulos.administracion.dto.response.AdministracionResponses.UsuarioAdminDetalle;
import shopify.api.core.modulos.administracion.servicio.AdministracionServicio;
import shopify.api.core.modulos.usuario.servicio.UsuarioAdminServicio;

/** Administración de plataforma (B8). Solo administrador de plataforma; para el resto, 404 SEG-006. */
@Tag(name = "Plataforma: administración", description = "Tiendas, usuarios, auditoría y pedidos de toda la plataforma")
@RestController
@RequestMapping("/plataforma")
@RequiredArgsConstructor
public class AdministracionControlador {

    private final AdministracionServicio administracion;
    private final UsuarioAdminServicio usuariosAdmin;

    public record MotivoBloqueoRequest(@NotBlank(message = "Indica el motivo") @Size(max = 255) String motivo) {
    }

    @Operation(summary = "Tiendas con dueño, plan y ventas (filtros: q, estado=ACTIVA|INACTIVA, plan)")
    @GetMapping("/tiendas")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_TIENDAS)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<TiendaAdmin>>> tiendas(
            @RequestParam(required = false) String q, @RequestParam(required = false) String estado,
            @RequestParam(required = false) String plan, @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(administracion.tiendas(q, estado, plan, pagina), "Tiendas");
    }

    @Operation(summary = "Detalle de una tienda: dueño, equipo, suscripción y balance")
    @GetMapping("/tiendas/{tiendaId}")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_TIENDAS)
    public ResponseEntity<RespuestaApi<TiendaAdminDetalle>> tienda(@PathVariable UUID tiendaId) {
        return RespuestaApi.ok(administracion.tienda(tiendaId), "Tienda");
    }

    @Operation(summary = "Usuarios (filtros: q, estado=ACTIVO|INACTIVO|BLOQUEADO)")
    @GetMapping("/usuarios")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_USUARIOS)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<UsuarioAdmin>>> usuarios(
            @RequestParam(required = false) String q, @RequestParam(required = false) String estado,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(administracion.usuarios(q, estado, pagina), "Usuarios");
    }

    @Operation(summary = "Detalle de un usuario con las tiendas en las que participa")
    @GetMapping("/usuarios/{usuarioId}")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_USUARIOS)
    public ResponseEntity<RespuestaApi<UsuarioAdminDetalle>> usuario(@PathVariable UUID usuarioId) {
        return RespuestaApi.ok(administracion.usuario(usuarioId), "Usuario");
    }

    @Operation(summary = "Bloquear una cuenta (cierra sus sesiones; queda auditado)")
    @PostMapping("/usuarios/{usuarioId}/bloquear")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_SEGURIDAD)
    public ResponseEntity<RespuestaApi<Void>> bloquear(@PathVariable UUID usuarioId,
                                                       @Valid @RequestBody MotivoBloqueoRequest request) {
        usuariosAdmin.bloquear(usuarioId, request.motivo());
        return RespuestaApi.ok(null, "Cuenta bloqueada");
    }

    @Operation(summary = "Desbloquear una cuenta")
    @PostMapping("/usuarios/{usuarioId}/desbloquear")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_SEGURIDAD)
    public ResponseEntity<RespuestaApi<Void>> desbloquear(@PathVariable UUID usuarioId) {
        usuariosAdmin.desbloquear(usuarioId);
        return RespuestaApi.ok(null, "Cuenta desbloqueada");
    }

    @Operation(summary = "Cerrar todas las sesiones de una cuenta")
    @PostMapping("/usuarios/{usuarioId}/cerrar-sesiones")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_SEGURIDAD)
    public ResponseEntity<RespuestaApi<Void>> cerrarSesiones(@PathVariable UUID usuarioId) {
        usuariosAdmin.cerrarSesiones(usuarioId);
        return RespuestaApi.ok(null, "Sesiones cerradas");
    }

    @Operation(summary = "Auditoría de solo lectura (filtros: usuario_id, entidad, accion, categoria=SEGURIDAD|NEGOCIO, desde, hasta)")
    @GetMapping("/auditoria")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_AUDITORIA)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<RegistroAuditoria>>> auditoria(
            @RequestParam(name = "usuario_id", required = false) UUID usuarioId,
            @RequestParam(required = false) String entidad, @RequestParam(required = false) String accion,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(
                administracion.auditoria(usuarioId, entidad, accion, categoria, desde, hasta, pagina), "Auditoría");
    }

    @Operation(summary = "Pedidos de la plataforma con cuántas órdenes tienen pagadas (búsqueda q por número o correo)")
    @GetMapping("/pedidos")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_PEDIDOS)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<PedidoAdmin>>> pedidos(
            @RequestParam(required = false) String q, @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(administracion.pedidos(q, pagina), "Pedidos");
    }
}
