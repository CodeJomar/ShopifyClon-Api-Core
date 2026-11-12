package shopify.api.core.modulos.balance.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;
import shopify.api.core.comun.seguridad.RequierePermiso;
import shopify.api.core.comun.seguridad.SoloAdministradorPlataforma;
import shopify.api.core.modulos.balance.dto.response.RepartoResponse;
import shopify.api.core.modulos.balance.servicio.RepartoServicio;

/** Libro de repartos (R-001, B6): lo que le toca a cada tienda de cada pago, con la comisión descontada. */
@Tag(name = "Balance: repartos", description = "Movimientos del libro de repartos")
@RestController
@RequiredArgsConstructor
public class RepartosControlador {

    private final RepartoServicio repartos;

    @Operation(summary = "Movimientos de reparto de la tienda (requiere X-Tienda-Id)")
    @GetMapping("/panel/balance/movimientos")
    @RequierePermiso(Permiso.METRICAS_VER)
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<RepartoResponse>>> movimientos(
            ContextoTienda contexto, @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(repartos.movimientosDeTienda(contexto.tiendaId(), pagina), "Movimientos de balance");
    }

    @Operation(summary = "Repartos de toda la plataforma, opcionalmente de un pedido (solo administrador)")
    @GetMapping("/plataforma/repartos")
    @SoloAdministradorPlataforma
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<RepartoResponse>>> deLaPlataforma(
            @RequestParam(name = "pedido_id", required = false) UUID pedidoId,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(repartos.deLaPlataforma(pedidoId, pagina), "Repartos");
    }
}
