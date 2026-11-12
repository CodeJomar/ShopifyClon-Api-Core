package shopify.api.core.modulos.pago.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.pago.dto.response.IniciarPagoResponse;
import shopify.api.core.modulos.pago.servicio.PagoEnLineaServicio;

/** Pago único de un pedido con tarjeta (R-001, B6). El acceso es el token del pedido, sin sesión. */
@Tag(name = "Pedidos: pago", description = "Un solo cobro por el total del pedido")
@RestController
@RequestMapping("/pedidos/{tokenAcceso}")
@RequiredArgsConstructor
public class PagoPedidoControlador {

    private final PagoEnLineaServicio pagoEnLinea;

    @Operation(summary = "Iniciar el pago con tarjeta de todo el pedido (devuelve el client_secret)")
    @PostMapping("/pagar")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<IniciarPagoResponse>> pagar(@PathVariable String tokenAcceso) {
        return RespuestaApi.ok(pagoEnLinea.iniciarPedido(tokenAcceso), "Pago iniciado");
    }
}
