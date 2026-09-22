package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.ObtenerProductoPorIdCasoUso;
import shopify.api.core.modules.catalogo.application.dto.response.ProductoDetalleResponse;
import shopify.api.core.shared.dto.ConsultaIndividualDto;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.UUID;

@Tag(name = "Catálogo - Productos")
@RestController
@RequestMapping("/catalogo/productos")
@RequiredArgsConstructor
public class ObtenerProductoPorIdControlador {

    private final ObtenerProductoPorIdCasoUso casoUso;

    @Operation(summary = "Obtener detalle completo de un producto con sus variantes (Público)")
    @GetMapping("/{id}")
    public ResponseEntity<RespuestaApi<ConsultaIndividualDto<ProductoDetalleResponse>>> obtenerPorId(@PathVariable("id") UUID id) {
        ProductoDetalleResponse respuesta = casoUso.ejecutar(id);
        return RespuestaApi.ok(ConsultaIndividualDto.de(respuesta), "Detalle de producto recuperado exitosamente");
    }
}