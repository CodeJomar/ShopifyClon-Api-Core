package shopify.api.core.modulos.campana.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RequierePermisoPlataforma;
import shopify.api.core.modulos.campana.dto.request.CambiarCampanaRequest;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanaActiva;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanasPlataforma;
import shopify.api.core.modulos.campana.servicio.CampanaServicio;

@Tag(name = "Plataforma: campañas", description = "Elegir la campaña comercial vigente (administradores de plataforma)")
@RestController
@RequestMapping("/plataforma/campanas")
@RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_PLANES)
@RequiredArgsConstructor
public class CampanaPlataformaControlador {

    private final CampanaServicio campanas;

    @Operation(summary = "Campaña vigente, campañas disponibles y tramos de descuento")
    @GetMapping
    public ResponseEntity<RespuestaApi<CampanasPlataforma>> estado() {
        return RespuestaApi.ok(campanas.estadoPlataforma(), "Campañas");
    }

    @Operation(summary = "Activar una campaña (código nulo la apaga; CAM-001 si no existe). Queda auditado")
    @PutMapping("/activa")
    public ResponseEntity<RespuestaApi<CampanaActiva>> cambiar(@RequestBody CambiarCampanaRequest solicitud) {
        return RespuestaApi.ok(campanas.cambiar(solicitud.codigo()), "Campaña actualizada");
    }
}
