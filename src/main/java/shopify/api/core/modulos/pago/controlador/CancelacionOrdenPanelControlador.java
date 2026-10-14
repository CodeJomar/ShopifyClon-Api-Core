package shopify.api.core.modulos.pago.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.modulos.pago.dto.request.CancelarOrdenRequest;
import shopify.api.core.modulos.pago.servicio.CancelacionOrdenServicio;

@Tag(name = "Panel: cancelación", description = "Cancelar órdenes no pagadas (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/ordenes/{ordenId}/cancelar")
@RequiredArgsConstructor
public class CancelacionOrdenPanelControlador {

    private final CancelacionOrdenServicio cancelacion;

    @Operation(summary = "Cancelar una orden no pagada (libera stock y devuelve el cupón)")
    @PostMapping
    @RequierePermiso(Permiso.ORDENES_CANCELAR)
    public ResponseEntity<RespuestaApi<Void>> cancelar(ContextoTienda contexto, @PathVariable UUID ordenId,
                                                       @Valid @RequestBody CancelarOrdenRequest request) {
        cancelacion.cancelarPorPersonal(contexto.tiendaId(), ordenId, request.motivo(), request.detalle());
        return RespuestaApi.ok(null, "Orden cancelada");
    }
}
