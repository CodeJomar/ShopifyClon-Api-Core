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

/** Pago en línea del comprador. Su acceso es el token de la orden (ORD-010), sin sesión. */
@Tag(name = "Vitrina: pagos", description = "Pago en línea de una orden")
@RestController
@RequestMapping("/vitrina/{subdominio}/ordenes/{tokenAcceso}")
@RequiredArgsConstructor
public class PagoEnLineaVitrinaControlador {

    private final PagoEnLineaServicio pagoEnLinea;

    @Operation(summary = "Iniciar el pago en línea de la orden (devuelve el client_secret)")
    @PostMapping("/pagar")
    @LimitarPeticiones(porMinuto = 10)
    public ResponseEntity<RespuestaApi<IniciarPagoResponse>> pagar(@PathVariable String subdominio,
                                                                  @PathVariable String tokenAcceso) {
        return RespuestaApi.ok(pagoEnLinea.iniciar(subdominio, tokenAcceso), "Pago iniciado");
    }
}
