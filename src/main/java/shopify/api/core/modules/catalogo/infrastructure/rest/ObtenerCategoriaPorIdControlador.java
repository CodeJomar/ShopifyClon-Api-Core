package shopify.api.core.modules.catalogo.infrastructure.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.modules.catalogo.application.casouso.ObtenerCategoriaPorIdCasoUso;
import shopify.api.core.modules.catalogo.application.dto.response.CategoriaResponse;
import shopify.api.core.shared.dto.ConsultaIndividualDto;
import shopify.api.core.shared.dto.RespuestaApi;

import java.util.UUID;

@Tag(name = "Catálogo - Categorías")
@RestController
@RequestMapping("/catalogo/categorias")
@RequiredArgsConstructor
public class ObtenerCategoriaPorIdControlador {

    private final ObtenerCategoriaPorIdCasoUso casoUso;

    @Operation(summary = "Obtener detalle de una categoría por su ID (Público)")
    @GetMapping("/{id}")
    public ResponseEntity<RespuestaApi<ConsultaIndividualDto<CategoriaResponse>>> obtenerPorId(
            @PathVariable("id") UUID id) {
        CategoriaResponse respuesta = casoUso.ejecutar(id);
        return RespuestaApi.ok(ConsultaIndividualDto.de(respuesta), "Categoría recuperada exitosamente");
    }
}