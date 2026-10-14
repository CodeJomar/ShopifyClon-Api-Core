package shopify.api.core.modulos.inventario.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import shopify.api.core.modulos.inventario.dto.request.AjusteInventarioRequest;
import shopify.api.core.modulos.inventario.dto.request.ConfiguracionInventarioRequest;
import shopify.api.core.modulos.inventario.dto.response.InventarioVarianteResponse;
import shopify.api.core.modulos.inventario.dto.response.MovimientoInventarioResponse;
import shopify.api.core.modulos.inventario.servicio.InventarioPanelServicio;

@Tag(name = "Panel: inventario", description = "Stock de las variantes (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/inventario/variantes")
@RequiredArgsConstructor
public class InventarioPanelControlador {

    private final InventarioPanelServicio inventarioPanel;

    @Operation(summary = "Ver cantidades y configuración de una variante")
    @GetMapping("/{varianteId}")
    @RequierePermiso(Permiso.INVENTARIO_AJUSTAR)
    public ResponseEntity<RespuestaApi<InventarioVarianteResponse>> detalle(
            ContextoTienda contexto, @PathVariable UUID varianteId) {
        return RespuestaApi.ok(inventarioPanel.detalle(contexto, varianteId), "Inventario obtenido");
    }

    @Operation(summary = "Ajustar la cantidad disponible (INV-007, con motivo y auditoría)")
    @PutMapping("/{varianteId}")
    @RequierePermiso(Permiso.INVENTARIO_AJUSTAR)
    public ResponseEntity<RespuestaApi<InventarioVarianteResponse>> ajustar(
            ContextoTienda contexto, @PathVariable UUID varianteId, @Valid @RequestBody AjusteInventarioRequest request) {
        return RespuestaApi.ok(inventarioPanel.ajustar(contexto, varianteId, request), "Inventario ajustado");
    }

    @Operation(summary = "Configurar el rastreo y la venta sin stock")
    @PutMapping("/{varianteId}/configuracion")
    @RequierePermiso(Permiso.INVENTARIO_AJUSTAR)
    public ResponseEntity<RespuestaApi<InventarioVarianteResponse>> configurar(
            ContextoTienda contexto, @PathVariable UUID varianteId,
            @RequestBody ConfiguracionInventarioRequest request) {
        return RespuestaApi.ok(inventarioPanel.configurar(contexto, varianteId, request), "Configuración actualizada");
    }

    @Operation(summary = "Historial de movimientos de la variante (más recientes primero)")
    @GetMapping("/{varianteId}/movimientos")
    @RequierePermiso(Permiso.INVENTARIO_AJUSTAR)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<MovimientoInventarioResponse>>> movimientos(
            ContextoTienda contexto,
            @PathVariable UUID varianteId,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta) {
        Page<MovimientoInventarioResponse> historial = inventarioPanel.movimientos(contexto, varianteId, pagina, desde, hasta);
        return RespuestaApi.paginado(historial, "Movimientos obtenidos");
    }
}
