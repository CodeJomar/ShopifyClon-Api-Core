package shopify.api.core.modules.tienda.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.tienda.application.casouso.ObtenerMisTiendasCasoUso;
import shopify.api.core.modules.tienda.application.dto.response.TiendaResponse;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.List;

@Tag(name = "Tiendas")
@RestController
@RequestMapping("/tiendas")
@RequiredArgsConstructor
public class ObtenerMisTiendasControlador {

    private final ObtenerMisTiendasCasoUso casoUso;

    @Operation(summary = "Listar todas las tiendas que administra el usuario autenticado", security = @SecurityRequirement(name = "BearerAuth"))
    @GetMapping("/mis-tiendas")
    public ResponseEntity<RespuestaApi<List<TiendaResponse>>> obtenerMisTiendas() {
        List<TiendaResponse> tiendas = casoUso.ejecutar();
        return RespuestaApi.ok(tiendas, "Tiendas del usuario recuperadas exitosamente");
    }
}