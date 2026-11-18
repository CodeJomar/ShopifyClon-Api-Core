package shopify.api.core.modulos.resena.controlador;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.comun.seguridad.PermisoPlataforma;
import shopify.api.core.comun.seguridad.RequierePermisoPlataforma;
import shopify.api.core.modulos.resena.dto.request.ResenaRequests.OcultarResenaRequest;
import shopify.api.core.modulos.resena.dto.request.ResenaRequests.ResenaRequest;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.Resena;
import shopify.api.core.modulos.resena.dto.response.ResenaResponses.ResenasProducto;
import shopify.api.core.modulos.resena.servicio.ResenaServicio;

/** Reseñas y calificaciones de productos (R-011). Leer es público; escribir requiere sesión y una compra pagada. */
@Tag(name = "Reseñas", description = "Calificaciones y comentarios de productos")
@RestController
@RequiredArgsConstructor
public class ResenaControlador {

    private final ResenaServicio resenas;

    @Operation(summary = "Resumen, mi reseña y lista de reseñas de un producto (orden: recientes, mejores, peores)")
    @GetMapping("/vitrina/{subdominio}/productos/{slug}/resenas")
    @LimitarPeticiones(porMinuto = 120)
    public ResponseEntity<RespuestaApi<ResenasProducto>> listar(
            @PathVariable String subdominio, @PathVariable String slug,
            @RequestParam(required = false) String orden, @RequestParam(name = "pagina", required = false) Integer pagina) {
        return RespuestaApi.ok(resenas.listar(subdominio, slug, orden, pagina), "Reseñas del producto");
    }

    @Operation(summary = "Reseñar un producto comprado (RES-001 sin compra, RES-002 miembro de la tienda, RES-003 duplicada)")
    @PostMapping("/vitrina/{subdominio}/productos/{slug}/resenas")
    @LimitarPeticiones(porMinuto = 20)
    public ResponseEntity<RespuestaApi<Resena>> crear(
            @PathVariable String subdominio, @PathVariable String slug, @Valid @RequestBody ResenaRequest request) {
        return RespuestaApi.creado(resenas.crear(subdominio, slug, request), "Reseña publicada");
    }

    @Operation(summary = "Editar mi reseña")
    @PutMapping("/cuenta/resenas/{resenaId}")
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<Resena>> editar(@PathVariable UUID resenaId, @Valid @RequestBody ResenaRequest request) {
        return RespuestaApi.ok(resenas.editar(resenaId, request), "Reseña actualizada");
    }

    @Operation(summary = "Eliminar mi reseña (puedo volver a reseñar el producto)")
    @DeleteMapping("/cuenta/resenas/{resenaId}")
    @LimitarPeticiones(porMinuto = 30)
    public ResponseEntity<RespuestaApi<Void>> eliminar(@PathVariable UUID resenaId) {
        resenas.eliminar(resenaId);
        return RespuestaApi.ok(null, "Reseña eliminada");
    }

    @Operation(summary = "Moderación: ocultar una reseña (queda auditado)")
    @PostMapping("/plataforma/resenas/{resenaId}/ocultar")
    @RequierePermisoPlataforma(PermisoPlataforma.PLATAFORMA_AUDITORIA)
    public ResponseEntity<RespuestaApi<Void>> ocultar(@PathVariable UUID resenaId, @Valid @RequestBody OcultarResenaRequest request) {
        resenas.ocultar(resenaId, request.motivo());
        return RespuestaApi.ok(null, "Reseña oculta");
    }
}
