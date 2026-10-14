package shopify.api.core.modulos.envio.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.envio.dto.response.TarifaVitrinaResponse;
import shopify.api.core.modulos.envio.servicio.TarifaEnvioServicio;

@Tag(name = "Vitrina: tarifas de envío", description = "Tarifas de envío activas de una tienda operativa")
@RestController
@RequestMapping("/vitrina/{subdominio}/tarifas-envio")
@RequiredArgsConstructor
public class TarifaEnvioVitrinaControlador {

    private final TarifaEnvioServicio tarifaEnvioServicio;

    @Operation(summary = "Listar las tarifas de envío activas (sin sesión)")
    @GetMapping
    public ResponseEntity<RespuestaApi<List<TarifaVitrinaResponse>>> listar(@PathVariable String subdominio) {
        return RespuestaApi.ok(tarifaEnvioServicio.listarActivasPorSubdominio(subdominio), "Tarifas de envío disponibles");
    }
}
