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
import shopify.api.core.modulos.catalogo.dto.request.VarianteEdicionRequest;
import shopify.api.core.modulos.catalogo.dto.request.VarianteRequest;
import shopify.api.core.modulos.catalogo.dto.response.ProductoDetalleResponse;
import shopify.api.core.modulos.catalogo.servicio.VarianteServicio;

@Tag(name = "Panel: variantes", description = "Variantes de un producto (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/catalogo")
@RequiredArgsConstructor
public class VariantePanelControlador {

    private final VarianteServicio varianteServicio;

    @Operation(summary = "Agregar una variante (crea su inventario inicial)")
    @PostMapping("/productos/{productoId}/variantes")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse.Variante>> agregar(
            ContextoTienda contexto, @PathVariable UUID productoId, @Valid @RequestBody VarianteRequest request) {
        return RespuestaApi.creado(varianteServicio.agregar(contexto, productoId, request), "Variante agregada");
    }

    @Operation(summary = "Editar una variante")
    @PutMapping("/variantes/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<ProductoDetalleResponse.Variante>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody VarianteEdicionRequest request) {
        return RespuestaApi.ok(varianteServicio.actualizar(contexto, id, request), "Variante actualizada");
    }

    @Operation(summary = "Eliminar una variante (no se borra la última, CAT-001)")
    @DeleteMapping("/variantes/{id}")
    @RequierePermiso(Permiso.CATALOGO_GESTIONAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        varianteServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Variante eliminada");
    }
}
