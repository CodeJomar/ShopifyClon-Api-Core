package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.AjustarStockVarianteCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.AjustarInventarioRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.UUID;

@Tag(name = "Catálogo - Inventario")
@RestController
@RequestMapping("/catalogo/variantes")
@RequiredArgsConstructor
public class AjustarStockVarianteControlador {

    private final AjustarStockVarianteCasoUso casoUso;

    @Operation(summary = "Ajustar stock de inventario de una variante", security = @SecurityRequirement(name = "BearerAuth"))
    @PatchMapping("/{varianteId}/inventario")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> ajustarStock(
            @PathVariable("varianteId") UUID varianteId,
            @Valid @RequestBody AjustarInventarioRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(varianteId, request);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}