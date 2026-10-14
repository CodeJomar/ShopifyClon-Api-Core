package shopify.api.core.modulos.pago.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
import shopify.api.core.modulos.pago.dto.request.ReembolsoRequest;
import shopify.api.core.modulos.pago.dto.response.ReembolsoResponse;
import shopify.api.core.modulos.pago.modelo.LineaReposicion;
import shopify.api.core.modulos.pago.servicio.ReembolsoServicio;

@Tag(name = "Panel: reembolsos", description = "Reembolsos parciales o totales (requiere X-Tienda-Id)")
@RestController
@RequestMapping("/panel/ordenes/{ordenId}/reembolsos")
@RequiredArgsConstructor
public class ReembolsoPanelControlador {

    private final ReembolsoServicio reembolsos;

    @Operation(summary = "Reembolsar parcial o totalmente una orden, con reposición opcional de stock")
    @PostMapping
    @RequierePermiso(Permiso.REEMBOLSOS_EMITIR)
    public ResponseEntity<RespuestaApi<ReembolsoResponse>> reembolsar(ContextoTienda contexto,
                                                                     @PathVariable UUID ordenId,
                                                                     @Valid @RequestBody ReembolsoRequest request) {
        List<LineaReposicion> reponer = request.reponer() == null ? List.of() : request.reponer().stream()
                .map(linea -> new LineaReposicion(linea.varianteId(), linea.cantidad()))
                .toList();
        return RespuestaApi.creado(reembolsos.reembolsar(contexto.tiendaId(), ordenId, request.monto(), request.motivo(),
                reponer), "Reembolso emitido");
    }
}
