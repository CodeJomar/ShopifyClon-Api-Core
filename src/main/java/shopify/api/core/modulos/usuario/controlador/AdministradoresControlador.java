package shopify.api.core.modulos.usuario.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RequierePermisoPlataforma;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.CambiarRolAdministradorRequest;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.EditarRolPlataformaRequest;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.NuevoAdministradorRequest;
import shopify.api.core.modulos.usuario.dto.request.AdministradoresRequests.RolPlataformaRequest;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.AdministradorResponse;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.PermisoPlataformaResponse;
import shopify.api.core.modulos.usuario.dto.response.AdministradoresResponses.RolPlataformaResponse;
import shopify.api.core.modulos.usuario.servicio.AdministradoresServicio;

/** Roles y administradores de plataforma (B9). Exige el permiso PLATAFORMA_ADMINISTRADORES. */
@Tag(name = "Plataforma: administradores", description = "Roles de plataforma y quién administra la plataforma")
@RestController
@RequestMapping("/plataforma")
@RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_ADMINISTRADORES)
@RequiredArgsConstructor
public class AdministradoresControlador {

    private final AdministradoresServicio administradores;

    @Operation(summary = "Catálogo de permisos de plataforma")
    @GetMapping("/permisos")
    public ResponseEntity<RespuestaApi<List<PermisoPlataformaResponse>>> permisos() {
        return RespuestaApi.ok(administradores.permisos(), "Permisos de plataforma");
    }

    @Operation(summary = "Roles de plataforma con sus permisos")
    @GetMapping("/roles")
    public ResponseEntity<RespuestaApi<List<RolPlataformaResponse>>> roles() {
        return RespuestaApi.ok(administradores.roles(), "Roles de plataforma");
    }

    @Operation(summary = "Crear un rol (solo con permisos que tú tienes)")
    @PostMapping("/roles")
    public ResponseEntity<RespuestaApi<RolPlataformaResponse>> crearRol(@Valid @RequestBody RolPlataformaRequest request) {
        return RespuestaApi.creado(administradores.crearRol(request), "Rol creado");
    }

    @Operation(summary = "Editar un rol que no sea de sistema")
    @PutMapping("/roles/{rolId}")
    public ResponseEntity<RespuestaApi<RolPlataformaResponse>> editarRol(
            @PathVariable UUID rolId, @Valid @RequestBody EditarRolPlataformaRequest request) {
        return RespuestaApi.ok(administradores.editarRol(rolId, request), "Rol actualizado");
    }

    @Operation(summary = "Eliminar un rol sin administradores asignados")
    @DeleteMapping("/roles/{rolId}")
    public ResponseEntity<RespuestaApi<Void>> eliminarRol(@PathVariable UUID rolId) {
        administradores.eliminarRol(rolId);
        return RespuestaApi.ok(null, "Rol eliminado");
    }

    @Operation(summary = "Administradores de plataforma (superadministradores y con rol)")
    @GetMapping("/administradores")
    public ResponseEntity<RespuestaApi<List<AdministradorResponse>>> administradores() {
        return RespuestaApi.ok(administradores.administradores(), "Administradores");
    }

    @Operation(summary = "Convertir a una persona en administradora con un rol")
    @PostMapping("/administradores")
    public ResponseEntity<RespuestaApi<AdministradorResponse>> agregar(@Valid @RequestBody NuevoAdministradorRequest request) {
        return RespuestaApi.creado(administradores.agregar(request.usuarioId(), request.rolId()), "Administrador agregado");
    }

    @Operation(summary = "Cambiar el rol de un administrador")
    @PutMapping("/administradores/{usuarioId}")
    public ResponseEntity<RespuestaApi<AdministradorResponse>> cambiarRol(
            @PathVariable UUID usuarioId, @Valid @RequestBody CambiarRolAdministradorRequest request) {
        return RespuestaApi.ok(administradores.cambiarRol(usuarioId, request.rolId()), "Rol actualizado");
    }

    @Operation(summary = "Quitarle la administración a una persona (deja de ser administradora)")
    @DeleteMapping("/administradores/{usuarioId}")
    public ResponseEntity<RespuestaApi<Void>> quitar(@PathVariable UUID usuarioId) {
        administradores.quitar(usuarioId);
        return RespuestaApi.ok(null, "Administrador quitado");
    }
}
