package shopify.api.core.modulos.tienda.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.tienda.dto.request.ActualizarTiendaRequest;
import shopify.api.core.modulos.tienda.dto.request.CambioEstadoTiendaRequest;
import shopify.api.core.modulos.tienda.dto.response.TiendaConfiguracionResponse;
import shopify.api.core.modulos.tienda.servicio.TiendaServicio;

@Tag(name = "Panel: tienda", description = "Configuración de la tienda (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/tienda")
@RequiredArgsConstructor
public class TiendaPanelControlador {

    private final TiendaServicio tiendaServicio;

    @Operation(summary = "Ver la configuración de la tienda (cualquier miembro)")
    @GetMapping
    public ResponseEntity<RespuestaApi<TiendaConfiguracionResponse>> ver(ContextoTienda contexto) {
        return RespuestaApi.ok(tiendaServicio.configuracion(contexto), "Configuración obtenida");
    }

    @Operation(summary = "Editar la configuración de la tienda")
    @PutMapping
    @RequierePermiso(Permiso.TIENDA_CONFIGURAR)
    public ResponseEntity<RespuestaApi<TiendaConfiguracionResponse>> actualizar(
            ContextoTienda contexto, @Valid @RequestBody ActualizarTiendaRequest request) {
        return RespuestaApi.ok(tiendaServicio.actualizarConfiguracion(contexto, request), "Configuración actualizada");
    }

    @Operation(summary = "Activar la tienda (solo el propietario)")
    @PostMapping("/activar")
    public ResponseEntity<RespuestaApi<TiendaConfiguracionResponse>> activar(
            ContextoTienda contexto, @Valid @RequestBody(required = false) CambioEstadoTiendaRequest request) {
        return RespuestaApi.ok(tiendaServicio.activar(contexto, motivo(request)), "Tienda activada");
    }

    @Operation(summary = "Desactivar la tienda (solo el propietario)")
    @PostMapping("/desactivar")
    public ResponseEntity<RespuestaApi<TiendaConfiguracionResponse>> desactivar(
            ContextoTienda contexto, @Valid @RequestBody(required = false) CambioEstadoTiendaRequest request) {
        return RespuestaApi.ok(tiendaServicio.desactivar(contexto, motivo(request)), "Tienda desactivada");
    }

    private static String motivo(CambioEstadoTiendaRequest request) {
        return request == null ? null : request.motivo();
    }
}
