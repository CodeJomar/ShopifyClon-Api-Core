package shopify.api.core.modulos.checkout.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.checkout.dto.request.CheckoutGlobalRequest;
import shopify.api.core.modulos.checkout.modelo.ResultadoCheckoutGlobal;
import shopify.api.core.modulos.checkout.servicio.CheckoutGlobalServicio;
import shopify.api.core.modulos.orden.dto.response.PedidoResponse;

/**
 * Checkout global: el carrito de {@code /carrito} (en {@code X-Carrito-Token}) se convierte en un pedido con una orden
 * hija por tienda. {@code Idempotency-Key} (UUID) es obligatoria: repetir la petición devuelve el mismo pedido con 200.
 */
@Tag(name = "Checkout global", description = "Convierte el carrito de varias tiendas en un pedido")
@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
public class CheckoutGlobalControlador {

    static final String ENCABEZADO_TOKEN = "X-Carrito-Token";
    static final String ENCABEZADO_CLAVE = "Idempotency-Key";

    private final CheckoutGlobalServicio checkout;

    @Operation(summary = "Crear el pedido y sus órdenes hijas desde el carrito (idempotente por Idempotency-Key)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 20)
    public ResponseEntity<RespuestaApi<PedidoResponse>> crear(
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @RequestHeader(value = ENCABEZADO_CLAVE, required = false) String clave,
            @Valid @RequestBody CheckoutGlobalRequest request) {
        ResultadoCheckoutGlobal resultado = checkout.crear(token, claveIdempotencia(clave), request);
        return resultado.nuevo()
                ? RespuestaApi.creado(resultado.pedido(), "Pedido creado")
                : RespuestaApi.ok(resultado.pedido(), "Pedido existente");
    }

    private static UUID claveIdempotencia(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new SolicitudInvalidaException("CHK-007", "Falta el encabezado Idempotency-Key");
        }
        try {
            return UUID.fromString(valor.strip());
        } catch (IllegalArgumentException ex) {
            throw new SolicitudInvalidaException("CHK-007", "Idempotency-Key debe ser un UUID");
        }
    }
}
