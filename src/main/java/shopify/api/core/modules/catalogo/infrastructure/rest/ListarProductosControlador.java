package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.ListarProductosCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.ProductoQueryInput;
import shopify.api.core.modules.catalogo.application.dto.response.ProductoResumenResponse;
import shopify.api.core.shared.dto.ConsultaPaginadaDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Catálogo - Productos")
@RestController
@RequestMapping("/catalogo/productos")
@RequiredArgsConstructor
public class ListarProductosControlador {

    private final ListarProductosCasoUso casoUso;

    @Operation(summary = "Listar productos con paginación y filtros (Público)")
    @GetMapping
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<ProductoResumenResponse>>> listar(
            @ModelAttribute ProductoQueryInput input) {
        ConsultaPaginadaDto<ProductoResumenResponse> resultado = casoUso.ejecutar(input);
        return RespuestaApi.ok(resultado, "Productos recuperados exitosamente");
    }
}