package shopify.api.core.modulos.campana.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.campana.dto.response.CampanaResponses.CampanaActiva;
import shopify.api.core.modulos.campana.servicio.CampanaServicio;

@Tag(name = "Campañas", description = "Campaña comercial vigente (pública)")
@RestController
@RequestMapping("/campanas")
@RequiredArgsConstructor
public class CampanaControlador {

    private final CampanaServicio campanas;

    @Operation(summary = "Campaña vigente y tramos de descuento (sin sesión)")
    @GetMapping("/activa")
    public ResponseEntity<RespuestaApi<CampanaActiva>> activa() {
        return RespuestaApi.ok(campanas.estadoPublico(), "Campaña vigente");
    }
}
