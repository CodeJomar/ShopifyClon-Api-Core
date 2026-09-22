package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.EliminarProductoCasoUso;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.UUID;

@Tag(name = "Catálogo - Productos")
@RestController
@RequestMapping("/catalogo/productos")
@RequiredArgsConstructor
public class EliminarProductoControlador {

    private final EliminarProductoCasoUso casoUso;

    @Operation(summary = "Eliminar lógicamente un producto (Soft-delete)", security = @SecurityRequirement(name = "BearerAuth"))
    @DeleteMapping("/{id}")
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> eliminar(@PathVariable("id") UUID id) {
        EstadoOperacionDto resultado = casoUso.ejecutar(id);
        return RespuestaApi.ok(resultado, resultado.getMensaje());
    }
}