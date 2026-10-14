package shopify.api.core.modulos.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.catalogo.dto.request.CategoriasRequest;
import shopify.api.core.modulos.catalogo.dto.request.ProductoDatosRequest;
import shopify.api.core.modulos.catalogo.dto.request.ProductoRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.dto.response.ProductoResumenResponse;
import shopify.api.core.modulos.catalogo.servicio.ProductoServicio;

@Tag(name = "Panel: productos", description = "Productos del catálogo (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/catalogo/productos")
@RequiredArgsConstructor
public class ProductoPanelControlador {

    private final ProductoServicio productoServicio;

    @Operation(summary = "Listar productos (paginado; búsqueda, publicado y categoría)")
    @GetMapping
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<ProductoResumenResponse>>> listar(
            ContextoTienda contexto,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) Boolean publicado,
            @RequestParam(name = "categoria_id", required = false) UUID categoriaId) {
        return RespuestaApi.paginado(
                productoServicio.listarPanel(contexto, pagina, texto, publicado, categoriaId), "Productos obtenidos");
    }

    @Operation(summary = "Ver un producto con sus variantes, imágenes y categorías")
    @GetMapping("/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> detalle(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(productoServicio.detalle(contexto, id), "Producto obtenido");
    }

    @Operation(summary = "Crear un producto con sus variantes (nace no publicado)")
    @PostMapping
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> crear(
            ContextoTienda contexto, @Valid @RequestBody ProductoRequest request) {
        return RespuestaApi.creado(productoServicio.crear(contexto, request), "Producto creado");
    }

    @Operation(summary = "Editar los datos del producto (sin variantes)")
    @PutMapping("/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody ProductoDatosRequest request) {
        return RespuestaApi.ok(productoServicio.actualizarDatos(contexto, id, request), "Producto actualizado");
    }

    @Operation(summary = "Publicar (exige precio y imagen principal, CAT-007)")
    @PostMapping("/{id}/publicar")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> publicar(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(productoServicio.publicar(contexto, id), "Producto publicado");
    }

    @Operation(summary = "Despublicar el producto")
    @PostMapping("/{id}/despublicar")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> despublicar(ContextoTienda contexto, @PathVariable UUID id) {
        return RespuestaApi.ok(productoServicio.despublicar(contexto, id), "Producto despublicado");
    }

    @Operation(summary = "Eliminar el producto (lógico)")
    @DeleteMapping("/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        productoServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Producto eliminado");
    }

    @Operation(summary = "Reemplazar las categorías del producto")
    @PutMapping("/{id}/categorias")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse>> categorias(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody CategoriasRequest request) {
        return RespuestaApi.ok(productoServicio.reemplazarCategorias(contexto, id, request.categoriaIds()),
                "Categorías actualizadas");
    }
}
