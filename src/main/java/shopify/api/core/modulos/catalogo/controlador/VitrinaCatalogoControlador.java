package shopify.api.core.modulos.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.modulos.catalogo.dto.response.VitrinaResponses;
import shopify.api.core.modulos.catalogo.servicio.VitrinaCatalogoServicio;

@Tag(name = "Vitrina: catálogo", description = "Catálogo público por subdominio")
@RestController
@RequestMapping("/vitrina/{subdominio}")
@RequiredArgsConstructor
public class VitrinaCatalogoControlador {

    private final VitrinaCatalogoServicio vitrina;

    @Operation(summary = "Categorías con productos publicados")
    @GetMapping("/categorias")
    public ResponseEntity<RespuestaApi<List<VitrinaResponses.Categoria>>> categorias(@PathVariable String subdominio) {
        return RespuestaApi.ok(vitrina.categorias(subdominio), "Categorías disponibles");
    }

    @Operation(summary = "Productos publicados (paginado; texto, categoría y orden)")
    @GetMapping("/productos")
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<VitrinaResponses.ProductoResumen>>> productos(
            @PathVariable String subdominio,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String orden) {
        return RespuestaApi.paginado(vitrina.productos(subdominio, pagina, texto, categoria, orden), "Productos");
    }

    @Operation(summary = "Detalle de un producto publicado por su identificador")
    @GetMapping("/productos/{slug}")
    public ResponseEntity<RespuestaApi<VitrinaResponses.ProductoDetalle>> detalle(
            @PathVariable String subdominio, @PathVariable String slug) {
        return RespuestaApi.ok(vitrina.detalle(subdominio, slug), "Producto");
    }
}
