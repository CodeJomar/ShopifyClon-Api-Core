package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.CrearCategoriaCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.CrearCategoriaRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Catálogo - Categorías", description = "Gestión de categorías del catálogo")
@RestController
@RequestMapping("/catalogo/categorias")
@RequiredArgsConstructor
public class CrearCategoriaControlador {

    private final CrearCategoriaCasoUso casoUso;

    @Operation(summary = "Crear nueva categoría", security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.creado(resultado, resultado.getMensaje());
    }
}