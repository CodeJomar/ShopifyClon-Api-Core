package shopify.api.core.modulos.suscripcion.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.suscripcion.dto.response.PlanResponse;
import shopify.api.core.modulos.suscripcion.servicio.PlanServicio;

@Tag(name = "Planes", description = "Planes de suscripción públicos de la plataforma")
@RestController
@RequestMapping("/planes")
@RequiredArgsConstructor
public class PlanControlador {

    private final PlanServicio planServicio;

    @Operation(summary = "Listar los planes públicos (sin sesión)")
    @GetMapping
    public ResponseEntity<RespuestaApi<List<PlanResponse>>> listar() {
        return RespuestaApi.ok(planServicio.listarPublicos(), "Planes disponibles");
    }
}
