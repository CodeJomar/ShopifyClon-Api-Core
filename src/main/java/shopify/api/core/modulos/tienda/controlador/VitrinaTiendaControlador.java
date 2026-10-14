package shopify.api.core.modulos.tienda.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.tienda.dto.response.TiendaVitrinaResponse;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

@Tag(name = "Vitrina", description = "Datos públicos de la tienda por subdominio")
@RestController
@RequestMapping("/vitrina/{subdominio}")
@RequiredArgsConstructor
public class VitrinaTiendaControlador {

    private final TiendaServicio tiendaServicio;

    @Operation(summary = "Ver los datos públicos de una tienda operativa")
    @GetMapping
    public ResponseEntity<RespuestaApi<TiendaVitrinaResponse>> ver(@PathVariable String subdominio) {
        return RespuestaApi.ok(tiendaServicio.vitrina(subdominio), "Tienda obtenida");
    }
}
