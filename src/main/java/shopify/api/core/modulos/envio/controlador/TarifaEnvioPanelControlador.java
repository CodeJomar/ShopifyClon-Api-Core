package shopify.api.core.modulos.envio.controlador;

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
import shopify.api.core.modulos.envio.dto.request.TarifaEnvioRequest;
import shopify.api.core.modulos.envio.dto.response.TarifaEnvioResponse;
import shopify.api.core.modulos.envio.servicio.TarifaEnvioServicio;

@Tag(name = "Panel: tarifas de envío", description = "Tarifas de envío de la tienda (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/envios/tarifas")
@RequiredArgsConstructor
public class TarifaEnvioPanelControlador {

    private final TarifaEnvioServicio tarifaEnvioServicio;

    @Operation(summary = "Listar las tarifas de envío (por orden, luego nombre)")
    @GetMapping
    @RequierePermiso(Permiso.ENVIOS_CONFIGURAR)
    public ResponseEntity<RespuestaApi<List<TarifaEnvioResponse>>> listar(ContextoTienda contexto) {
        return RespuestaApi.ok(tarifaEnvioServicio.listar(contexto), "Tarifas obtenidas");
    }

    @Operation(summary = "Crear una tarifa de envío")
    @PostMapping
    @RequierePermiso(Permiso.ENVIOS_CONFIGURAR)
    public ResponseEntity<RespuestaApi<TarifaEnvioResponse>> crear(
            ContextoTienda contexto, @Valid @RequestBody TarifaEnvioRequest request) {
        return RespuestaApi.creado(tarifaEnvioServicio.crear(contexto, request), "Tarifa creada");
    }

    @Operation(summary = "Editar una tarifa de envío")
    @PutMapping("/{id}")
    @RequierePermiso(Permiso.ENVIOS_CONFIGURAR)
    public ResponseEntity<RespuestaApi<TarifaEnvioResponse>> actualizar(
            ContextoTienda contexto, @PathVariable UUID id, @Valid @RequestBody TarifaEnvioRequest request) {
        return RespuestaApi.ok(tarifaEnvioServicio.actualizar(contexto, id, request), "Tarifa actualizada");
    }

    @Operation(summary = "Eliminar una tarifa de envío (lógico)")
    @DeleteMapping("/{id}")
    @RequierePermiso(Permiso.ENVIOS_CONFIGURAR)
    public ResponseEntity<RespuestaApi<Void>> eliminar(ContextoTienda contexto, @PathVariable UUID id) {
        tarifaEnvioServicio.eliminar(contexto, id);
        return RespuestaApi.ok(null, "Tarifa eliminada");
    }
}
