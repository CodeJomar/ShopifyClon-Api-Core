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
import shopify.api.core.modulos.usuario.dto.request.DireccionRequest;
import shopify.api.core.modulos.usuario.dto.response.DireccionResponse;
import shopify.api.core.modulos.usuario.servicio.DireccionServicio;

@Tag(name = "Direcciones", description = "Libreta de direcciones del usuario en sesión")
@RestController
@RequestMapping("/cuenta/direcciones")
@RequiredArgsConstructor
public class DireccionControlador {

    private final DireccionServicio direccionServicio;

    @Operation(summary = "Listar mis direcciones (la principal primero)")
    @GetMapping
    public ResponseEntity<RespuestaApi<List<DireccionResponse>>> listar() {
        return RespuestaApi.ok(direccionServicio.listar(), "Direcciones obtenidas");
    }

    @Operation(summary = "Agregar una dirección")
    @PostMapping
    public ResponseEntity<RespuestaApi<DireccionResponse>> crear(@Valid @RequestBody DireccionRequest request) {
        return RespuestaApi.creado(direccionServicio.crear(request), "Dirección guardada");
    }

    @Operation(summary = "Editar una dirección")
    @PutMapping("/{id}")
    public ResponseEntity<RespuestaApi<DireccionResponse>> actualizar(
            @PathVariable UUID id, @Valid @RequestBody DireccionRequest request) {
        return RespuestaApi.ok(direccionServicio.actualizar(id, request), "Dirección actualizada");
    }

    @Operation(summary = "Eliminar una dirección")
    @DeleteMapping("/{id}")
    public ResponseEntity<RespuestaApi<Void>> eliminar(@PathVariable UUID id) {
        direccionServicio.eliminar(id);
        return RespuestaApi.ok(null, "Dirección eliminada");
    }

    @Operation(summary = "Marcar una dirección como principal")
    @PostMapping("/{id}/principal")
    public ResponseEntity<RespuestaApi<DireccionResponse>> marcarPrincipal(@PathVariable UUID id) {
        return RespuestaApi.ok(direccionServicio.marcarPrincipal(id), "Dirección principal actualizada");
    }
}
