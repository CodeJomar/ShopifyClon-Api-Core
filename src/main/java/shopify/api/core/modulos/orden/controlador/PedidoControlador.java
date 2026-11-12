package shopify.api.core.modulos.orden.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.ConsultaPaginadaDto;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.comun.seguridad.SesionActual;
import shopify.api.core.modulos.orden.dto.response.PedidoResponse;
import shopify.api.core.modulos.orden.servicio.PedidoConsultaServicio;

/** Pedidos del comprador (R-001, B5): una compra con una orden hija por tienda. Solo consulta. */
@Tag(name = "Pedidos", description = "Compras con productos de varias tiendas")
@RestController
@RequiredArgsConstructor
public class PedidoControlador {

    private final PedidoConsultaServicio consultas;

    @Operation(summary = "Estado del pedido para el comprador invitado, por su token de acceso")
    @GetMapping("/pedidos/{tokenAcceso}")
    @LimitarPeticiones(porMinuto = 60)
    public ResponseEntity<RespuestaApi<PedidoResponse>> porToken(@PathVariable String tokenAcceso) {
        return RespuestaApi.ok(consultas.porToken(tokenAcceso), "Pedido");
    }

    @Operation(summary = "Historial de pedidos del comprador registrado")
    @GetMapping("/cuenta/pedidos")
    public ResponseEntity<RespuestaApi<ConsultaPaginadaDto<PedidoResponse>>> historial(
            @RequestParam(name = "pagina", defaultValue = "0") int pagina) {
        return RespuestaApi.paginado(consultas.historialDelUsuario(SesionActual.usuarioId(), pagina), "Mis pedidos");
    }

    @Operation(summary = "Detalle de un pedido del comprador registrado")
    @GetMapping("/cuenta/pedidos/{pedidoId}")
    public ResponseEntity<RespuestaApi<PedidoResponse>> detalle(@PathVariable UUID pedidoId) {
        return RespuestaApi.ok(consultas.detalleDelUsuario(SesionActual.usuarioId(), pedidoId), "Pedido");
    }
}
