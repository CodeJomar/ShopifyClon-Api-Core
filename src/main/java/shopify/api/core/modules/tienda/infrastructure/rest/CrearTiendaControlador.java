package shopify.api.core.modules.tienda.infrastructure.rest;

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
import shopify.api.core.modules.tienda.application.casouso.CrearTiendaCasoUso;
import shopify.api.core.modules.tienda.application.dto.request.CrearTiendaRequest;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Tiendas", description = "Gestión de tiendas y configuración multi-tenant")
@RestController
@RequestMapping("/tiendas")
@RequiredArgsConstructor
public class CrearTiendaControlador {

    private final CrearTiendaCasoUso casoUso;

    @Operation(summary = "Crear una nueva tienda asociada al usuario autenticado", security = @SecurityRequirement(name = "BearerAuth"))
    @PostMapping
    public ResponseEntity<RespuestaApi<EstadoOperacionDto>> crear(@Valid @RequestBody CrearTiendaRequest request) {
        EstadoOperacionDto resultado = casoUso.ejecutar(request);
        return RespuestaApi.creado(resultado, resultado.getMensaje());
    }
}