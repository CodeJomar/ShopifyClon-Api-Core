package shopify.api.core.modulos.mercado.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
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
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.CategoriaMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.DestacadosMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.ProductoMercadoResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.TiendaMercadoDetalleResponse;
import shopify.api.core.modulos.mercado.dto.response.MercadoResponses.TiendaMercadoResponse;
import shopify.api.core.modulos.mercado.servicio.MercadoServicio;

@Tag(name = "Mercado", description = "Productos y tiendas de todas las tiendas visibles (público, sin sesión)")
@RestController
@RequestMapping("/mercado")
@RequiredArgsConstructor
public class MercadoControlador {

    private final MercadoServicio mercadoServicio;

    @Operation(summary = "Productos publicados de todas las tiendas visibles, con búsqueda, filtros y orden")
    @GetMapping("/productos")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<ProductoMercadoResponse>>> productos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String tienda,
            @RequestParam(name = "precio_min", required = false) BigDecimal precioMin,
            @RequestParam(name = "precio_max", required = false) BigDecimal precioMax,
            @RequestParam(required = false) String orden,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return RespuestaApi.paginado(
                mercadoServicio.productos(q, categoria, tienda, precioMin, precioMax, orden, pagina, tamano),
                "Productos del mercado");
    }

    @Operation(summary = "Tiendas visibles (con al menos un producto publicado), con búsqueda por nombre")
    @GetMapping("/tiendas")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<TiendaMercadoResponse>>> tiendas(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamano) {
        return RespuestaApi.paginado(mercadoServicio.tiendas(q, pagina, tamano), "Tiendas del mercado");
    }

    @Operation(summary = "Ficha pública de una tienda visible, con sus categorías")
    @GetMapping("/tiendas/{subdominio}")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<TiendaMercadoDetalleResponse>> tienda(@PathVariable String subdominio) {
        return RespuestaApi.ok(mercadoServicio.tienda(subdominio), "Tienda del mercado");
    }

    @Operation(summary = "Categorías con productos visibles, agrupadas entre tiendas")
    @GetMapping("/categorias")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<List<CategoriaMercadoResponse>>> categorias() {
        return RespuestaApi.ok(mercadoServicio.categorias(), "Categorías del mercado");
    }

    @Operation(summary = "Tiendas y productos recientes para la landing, con el total de cada cosa")
    @GetMapping("/destacados")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<DestacadosMercadoResponse>> destacados() {
        return RespuestaApi.ok(mercadoServicio.destacados(), "Destacados del mercado");
    }
}
