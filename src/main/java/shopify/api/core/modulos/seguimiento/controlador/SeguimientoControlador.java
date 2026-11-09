package shopify.api.core.modulos.seguimiento.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.seguimiento.dto.response.SeguimientoPublicoResponse;
import shopify.api.core.modulos.seguimiento.servicio.SeguimientoServicio;

@Tag(name = "Seguimiento público", description = "Estado de un envío por número de guía, sin sesión")
@RestController
@RequestMapping("/seguimiento")
@RequiredArgsConstructor
public class SeguimientoControlador {

    private final SeguimientoServicio seguimientoServicio;

    @Operation(summary = "Seguimiento de un envío por número de guía (público, sin datos personales)")
    @GetMapping("/{numeroGuia}")
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<SeguimientoPublicoResponse>> ver(@PathVariable String numeroGuia) {
        return RespuestaApi.ok(seguimientoServicio.porNumeroGuia(numeroGuia), "Seguimiento del envío");
    }
}
