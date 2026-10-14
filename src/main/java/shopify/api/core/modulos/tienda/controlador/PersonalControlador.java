package shopify.api.core.modulos.tienda.controlador;

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
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.tienda.dto.request.AgregarPersonalRequest;
import shopify.api.core.modulos.tienda.dto.request.CambiarRolRequest;
import shopify.api.core.modulos.tienda.dto.request.QuitarPersonalRequest;
import shopify.api.core.modulos.tienda.dto.response.MiembroPersonalResponse;
import shopify.api.core.modulos.tienda.servicio.PersonalServicio;

@Tag(name = "Panel: personal", description = "Personal de la tienda y sus roles (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/personal")
@RequiredArgsConstructor
public class PersonalControlador {

    private final PersonalServicio personalServicio;

    @Operation(summary = "Listar el personal (el propietario primero)")
    @GetMapping
    @RequierePermiso(Permiso.PERSONAL_GESTIONAR)
    public ResponseEntity<RespuestaApi<List<MiembroPersonalResponse>>> listar(ContextoTienda contexto) {
        return RespuestaApi.ok(personalServicio.listar(contexto), "Personal obtenido");
    }

    @Operation(summary = "Agregar a una persona con cuenta activa al personal")
    @PostMapping
    @RequierePermiso(Permiso.PERSONAL_GESTIONAR)
    public ResponseEntity<RespuestaApi<MiembroPersonalResponse>> agregar(
            ContextoTienda contexto, @Valid @RequestBody AgregarPersonalRequest request) {
        return RespuestaApi.creado(personalServicio.agregar(contexto, request), "Persona agregada al personal");
    }

    @Operation(summary = "Cambiar el rol de un miembro del personal")
    @PutMapping("/{usuarioId}")
    @RequierePermiso(Permiso.PERSONAL_GESTIONAR)
    public ResponseEntity<RespuestaApi<MiembroPersonalResponse>> cambiarRol(
            ContextoTienda contexto, @PathVariable UUID usuarioId, @Valid @RequestBody CambiarRolRequest request) {
        return RespuestaApi.ok(personalServicio.cambiarRol(contexto, usuarioId, request), "Rol actualizado");
    }

    @Operation(summary = "Quitar a una persona del personal")
    @DeleteMapping("/{usuarioId}")
    @RequierePermiso(Permiso.PERSONAL_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> quitar(
            ContextoTienda contexto, @PathVariable UUID usuarioId, @Valid @RequestBody QuitarPersonalRequest request) {
        personalServicio.quitar(contexto, usuarioId, request);
        return RespuestaApi.ok(null, "Persona quitada del personal");
    }
}
