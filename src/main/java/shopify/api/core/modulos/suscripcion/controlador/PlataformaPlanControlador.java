package shopify.api.core.modulos.suscripcion.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;
import shopify.api.core.modulos.suscripcion.dto.request.ActualizarPlanRequest;
import shopify.api.core.modulos.suscripcion.dto.request.CrearPlanRequest;
import shopify.api.core.modulos.suscripcion.dto.response.PlanPlataformaResponse;
import shopify.api.core.modulos.suscripcion.servicio.PlataformaPlanServicio;

@Tag(name = "Plataforma: planes", description = "Catálogo de planes (solo administradores de plataforma)")
@RestController
@RequestMapping("/plataforma/planes")
@SoloAdministradorPlataforma
@RequiredArgsConstructor
public class PlataformaPlanControlador {

    private final PlataformaPlanServicio planServicio;

    @Operation(summary = "Listar todos los planes, también los no públicos")
    @GetMapping
    public ResponseEntity<RespuestaApi<List<PlanPlataformaResponse>>> listar() {
        return RespuestaApi.ok(planServicio.listar(), "Planes obtenidos");
    }

    @Operation(summary = "Crear un plan")
    @PostMapping
    public ResponseEntity<RespuestaApi<PlanPlataformaResponse>> crear(@Valid @RequestBody CrearPlanRequest request) {
        return RespuestaApi.creado(planServicio.crear(request), "Plan creado");
    }

    @Operation(summary = "Editar un plan (el código no cambia)")
    @PutMapping("/{codigo}")
    public ResponseEntity<RespuestaApi<PlanPlataformaResponse>> actualizar(
            @PathVariable String codigo, @Valid @RequestBody ActualizarPlanRequest request) {
        return RespuestaApi.ok(planServicio.actualizar(codigo, request), "Plan actualizado");
    }

    @Operation(summary = "Retirar un plan del catálogo público (nunca borra)")
    @DeleteMapping("/{codigo}")
    public ResponseEntity<RespuestaApi<Void>> desactivar(@PathVariable String codigo) {
        planServicio.desactivar(codigo);
        return RespuestaApi.ok(null, "Plan retirado del catálogo público");
    }
}
