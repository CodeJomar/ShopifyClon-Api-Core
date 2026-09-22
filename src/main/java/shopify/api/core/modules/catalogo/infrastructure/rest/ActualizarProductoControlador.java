package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.ActualizarProductoCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.ActualizarProductoRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.UUID;

@Tag(name = "Catálogo - Productos")
@RestController
@RequestMapping("/catalogo/productos")
@RequiredArgsConstructor
public class ActualizarProductoControlador {

    private final ActualizarProductoCasoUso casoUso;

    @Operation(summary = "Actualizar información del producto", security = @SecurityRequirement(name = "BearerAuth"))
    @PutMapping("/{id}")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> actualizar(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ActualizarProductoRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(id, request);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}