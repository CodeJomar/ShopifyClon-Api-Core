package shopify.api.core.modulos.checkout.controlador;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shopify.api.core.comun.dto.RespuestaApi;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.LimitarPeticiones;
import shopify.api.core.modulos.checkout.dto.request.CheckoutRequest;
import shopify.api.core.modulos.checkout.dto.response.CheckoutResponse;
import shopify.api.core.modulos.checkout.modelo.ResultadoCheckout;
import shopify.api.core.modulos.checkout.servicio.CheckoutServicio;

/**
 * Checkout público. El carrito viaja en {@code X-Carrito-Token} y la clave de idempotencia en
 * {@code Idempotency-Key} (UUID, obligatoria: CHK-007). Repetir la misma petición devuelve la misma orden con 200.
 */
@Tag(name = "Vitrina: checkout", description = "Convierte el carrito en una orden")
@RestController
@RequestMapping("/vitrina/{subdominio}/checkout")
@RequiredArgsConstructor
public class CheckoutVitrinaControlador {

    static final String ENCABEZADO_TOKEN = "X-Carrito-Token";
    static final String ENCABEZADO_CLAVE = "Idempotency-Key";

    private final CheckoutServicio checkout;

    @Operation(summary = "Crear la orden desde el carrito (idempotente por Idempotency-Key)")
    @PostMapping
    @LimitarPeticiones(porMinuto = 20)
    public ResponseEntity<RespuestaApi<CheckoutResponse>> crear(
            @PathVariable String subdominio,
            @RequestHeader(value = ENCABEZADO_TOKEN, required = false) String token,
            @RequestHeader(value = ENCABEZADO_CLAVE, required = false) String clave,
            @Valid @RequestBody CheckoutRequest request) {
        ResultadoCheckout resultado = checkout.crear(subdominio, token, claveIdempotencia(clave), request);
        return resultado.nueva()
                ? RespuestaApi.creado(resultado.respuesta(), "Orden creada")
                : RespuestaApi.ok(resultado.respuesta(), "Orden existente");
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
