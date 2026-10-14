package shopify.api.core.modulos.suscripcion.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;
import shopify.api.core.modulos.suscripcion.dto.request.MotivoSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.response.SuscripcionPlataformaResponse;
import shopify.api.core.modulos.suscripcion.servicio.PlataformaSuscripcionServicio;

@Tag(name = "Plataforma: suscripciones", description = "Suscripciones de todas las tiendas (solo administradores de plataforma)")
@RestController
@RequestMapping("/plataforma")
@SoloAdministradorPlataforma
@RequiredArgsConstructor
public class PlataformaSuscripcionControlador {

    private final PlataformaSuscripcionServicio suscripcionServicio;

    @Operation(summary = "Listar suscripciones (paginado, filtro por estado)")
    @GetMapping("/suscripciones")
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<SuscripcionPlataformaResponse>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(suscripcionServicio.listar(estado, pagina), "Suscripciones obtenidas");
    }

    @Operation(summary = "Suspender una tienda (SUS-010)")
    @PostMapping("/tiendas/{tiendaId}/suspender")
    public ResponseEntity<RespuestaApi<Void>> suspender(
            @PathVariable UUID tiendaId, @Valid @RequestBody MotivoSuscripcionRequest request) {
        suscripcionServicio.suspender(tiendaId, request.motivo());
        return RespuestaApi.ok(null, "Tienda suspendida");
    }

    @Operation(summary = "Reactivar una tienda suspendida (vuelve al estado anterior)")
    @PostMapping("/tiendas/{tiendaId}/reactivar")
    public ResponseEntity<RespuestaApi<Void>> reactivar(
            @PathVariable UUID tiendaId, @Valid @RequestBody MotivoSuscripcionRequest request) {
        suscripcionServicio.reactivar(tiendaId, request.motivo());
        return RespuestaApi.ok(null, "Tienda reactivada");
    }
}
