package shopify.api.core.modulos.tienda.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.tienda.dto.response.OpcionesAparienciaResponse;
import shopify.api.core.modulos.tienda.servicio.AparienciaTiendaServicio;

@Tag(name = "Apariencia", description = "Opciones de personalización de la vitrina (públicas)")
@RestController
@RequestMapping("/apariencia")
@RequiredArgsConstructor
public class AparienciaControlador {

    private final AparienciaTiendaServicio apariencia;

    @Operation(summary = "Códigos válidos de cada opción de apariencia y sus valores por defecto (sin sesión)")
    @GetMapping("/opciones")
    public ResponseEntity<RespuestaApi<OpcionesAparienciaResponse>> opciones() {
        return RespuestaApi.ok(apariencia.opciones(), "Opciones de apariencia");
    }
}
