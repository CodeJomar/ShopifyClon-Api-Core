package shopify.api.core.modulos.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.catalogo.dto.request.CategoriaRequest;
import shopify.api.core.modulos.catalogo.dto.response.CategoriaResponse;
import shopify.api.core.modulos.catalogo.servicio.CategoriaServicio;

@Tag(name = "Panel: categorías", description = "Categorías del catálogo (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/catalogo/categorias")
@RequiredArgsConstructor
public class CategoriaPanelControlador {

    private final CategoriaServicio categoriaServicio;

    @Operation(summary = "Listar las categorías de la tienda")
    @GetMapping
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<List<CategoriaResponse>>> listar(ContextoTienda contexto) {
        return RespuestaApi.ok(categoriaServicio.listar(contexto), "Categorías obtenidas");
    }

    @Operation(summary = "Crear una categoría")
    @PostMapping
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<CategoriaResponse>> crear(
            ContextoTienda contexto, @Valid @RequestBody CategoriaRequest request) {
        return RespuestaApi.creado(categoriaServicio.crear(contexto, request), "Categoría creada");
    }

    @Operation(summary = "Editar una categoría")
    @PutMapping("/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<CategoriaResponse>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody CategoriaRequest request) {
        return RespuestaApi.ok(categoriaServicio.actualizar(contexto, id, request), "Categoría actualizada");
    }

    @Operation(summary = "Eliminar una categoría (lógico)")
    @DeleteMapping("/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        categoriaServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Categoría eliminada");
    }
}
