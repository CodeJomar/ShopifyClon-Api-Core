package shopify.api.core.modulos.suscripcion.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.suscripcion.dto.request.CambiarPlanSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.request.MotivoSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.request.PagarSuscripcionRequest;
import shopify.api.core.modulos.suscripcion.dto.response.SuscripcionPanelResponse;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionPanelServicio;

@Tag(name = "Panel: suscripción", description = "Suscripción de la tienda, solo propietario (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/suscripcion")
@RequiredArgsConstructor
public class SuscripcionPanelControlador {

    private final SuscripcionPanelServicio suscripcionPanelServicio;

    @Operation(summary = "Ver la suscripción, su período y el uso frente a los límites")
    @GetMapping
    @RequierePermiso(Permiso.SUSCRIPCION_GESTIONAR)
    public ResponseEntity<RespuestaApi<SuscripcionPanelResponse>> ver(ContextoTienda contexto) {
        return RespuestaApi.ok(suscripcionPanelServicio.ver(contexto), "Suscripción obtenida");
    }

    @Operation(summary = "Pagar el período (activa la suscripción)")
    @PostMapping("/pagar")
    @RequierePermiso(Permiso.SUSCRIPCION_GESTIONAR)
    public ResponseEntity<RespuestaApi<SuscripcionPanelResponse>> pagar(
            ContextoTienda contexto, @Valid @RequestBody PagarSuscripcionRequest request) {
        return RespuestaApi.ok(suscripcionPanelServicio.pagar(contexto, request), "Suscripción activa");
    }

    @Operation(summary = "Cambiar de plan (en prueba sin cobro; en activa con cobro)")
    @PostMapping("/cambiar-plan")
    @RequierePermiso(Permiso.SUSCRIPCION_GESTIONAR)
    public ResponseEntity<RespuestaApi<SuscripcionPanelResponse>> cambiarPlan(
            ContextoTienda contexto, @Valid @RequestBody CambiarPlanSuscripcionRequest request) {
        return RespuestaApi.ok(suscripcionPanelServicio.cambiarPlan(contexto, request), "Plan actualizado");
    }

    @Operation(summary = "Cancelar la suscripción (inmediata en prueba; al final del período en activa)")
    @PostMapping("/cancelar")
    @RequierePermiso(Permiso.SUSCRIPCION_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> cancelar(
            ContextoTienda contexto, @Valid @RequestBody MotivoSuscripcionRequest request) {
        suscripcionPanelServicio.cancelar(contexto, request.motivo());
        return RespuestaApi.ok(null, "Cancelación registrada");
    }

    @Operation(summary = "Revertir una cancelación programada")
    @PostMapping("/revertir-cancelacion")
    @RequierePermiso(Permiso.SUSCRIPCION_GESTIONAR)
    public ResponseEntity<RespuestaApi<SuscripcionPanelResponse>> revertirCancelacion(ContextoTienda contexto) {
        return RespuestaApi.ok(suscripcionPanelServicio.revertirCancelacion(contexto), "Cancelación revertida");
    }
}
