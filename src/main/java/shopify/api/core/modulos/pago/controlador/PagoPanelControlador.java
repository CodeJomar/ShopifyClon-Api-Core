package shopify.api.core.modulos.pago.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.pago.dto.request.RegistrarPagoManualRequest;
import shopify.api.core.modulos.pago.dto.response.PagoResponse;
import shopify.api.core.modulos.pago.servicio.PagoServicio;

@Tag(name = "Panel: pagos", description = "Pagos de una orden (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/ordenes/{ordenId}/pagos")
@RequiredArgsConstructor
public class PagoPanelControlador {

    private final PagoServicio pagos;

    @Operation(summary = "Registrar un pago manual (confirma la orden si estaba pendiente)")
    @PostMapping("/manual")
    @RequierePermiso(Permiso.ORDENES_GESTIONAR)
    public ResponseEntity<RespuestaApi<PagoResponse>> registrarManual(
            ContextoTienda contexto, @PathVariable UUID ordenId,
            @Valid @RequestBody(required = false) RegistrarPagoManualRequest request) {
        String referencia = request == null ? null : request.referencia();
        return RespuestaApi.creado(pagos.registrarManual(contexto.tiendaId(), ordenId, referencia), "Pago registrado");
    }

    @Operation(summary = "Pagos y reembolsos de la orden")
    @GetMapping
    @RequierePermiso(Permiso.ORDENES_VER)
    public ResponseEntity<RespuestaApi<List<PagoResponse>>> listar(ContextoTienda contexto, @PathVariable UUID ordenId) {
        return RespuestaApi.ok(pagos.listar(contexto.tiendaId(), ordenId), "Pagos de la orden");
    }
}
