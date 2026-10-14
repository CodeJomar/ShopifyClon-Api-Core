package shopify.api.core.modulos.catalogo.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import shopify.api.core.modulos.catalogo.dto.request.ImagenRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.servicio.ImagenServicio;

@Tag(name = "Panel: imágenes", description = "Imágenes de un producto (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/catalogo")
@RequiredArgsConstructor
public class ImagenPanelControlador {

    private final ImagenServicio imagenServicio;

    @Operation(summary = "Agregar una imagen al producto")
    @PostMapping("/productos/{productoId}/imagenes")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse.Imagen>> agregar(
            ContextoTienda contexto, @PathVariable UUID productoId, @Valid @RequestBody ImagenRequest request) {
        return RespuestaApi.creado(imagenServicio.agregar(contexto, productoId, request), "Imagen agregada");
    }

    @Operation(summary = "Editar una imagen (marcar principal desmarca la anterior)")
    @PutMapping("/imagenes/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse.Imagen>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody ImagenRequest request) {
        return RespuestaApi.ok(imagenServicio.actualizar(contexto, id, request), "Imagen actualizada");
    }

    @Operation(summary = "Eliminar una imagen (si era principal, la siguiente pasa a serlo)")
    @DeleteMapping("/imagenes/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        imagenServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Imagen eliminada");
    }
}
