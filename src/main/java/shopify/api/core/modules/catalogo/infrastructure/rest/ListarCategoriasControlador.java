package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.ListarCategoriasCasoUso;
import shopify.api.core.modules.catalogo.application.dto.request.CategoriaQueryInput;
import shopify.api.core.modules.catalogo.application.dto.response.CategoriaResponse;
import shopify.api.core.shared.dto.ConsultaPaginadaDto;
import shopify.api.core.shared.dto.RespuestaApi;

@Tag(name = "Catálogo - Categorías")
@RestController
@RequestMapping("/catalogo/categorias")
@RequiredArgsConstructor
public class ListarCategoriasControlador {

    private final ListarCategoriasCasoUso casoUso;

    @Operation(summary = "Listar categorías con paginación y búsqueda (Público)")
    @GetMapping
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<CategoriaResponse>>> listar(
            @ModelAttribute CategoriaQueryInput input) {
        ConsultaPaginadaDto<CategoriaResponse> resultado = casoUso.ejecutar(input);
        return RespuestaApi.ok(resultado, "Categorías recuperadas exitosamente");
    }
}