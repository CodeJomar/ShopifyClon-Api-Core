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
import shopify.api.core.modules.catalogo.application.casouso.CrearProductoCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.CrearProductoRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Catálogo - Productos", description = "Gestión de productos, variantes e inventario")
@RestController
@RequestMapping("/catalogo/productos")
@RequiredArgsConstructor
public class CrearProductoControlador {

    private final CrearProductoCasoUso casoUso;

    @Operation(summary = "Crear nuevo producto con variante inicial e inventario", security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> crear(@Valid @RequestBody CrearProductoRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.creado(resultado, resultado.getMensaje());
    }
}