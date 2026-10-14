package shopify.api.core.modulos.checkout.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/**
 * Datos del comprador y del pago. Ningún monto lo pone el cliente: {@code totalEsperado} es solo lo que vio, y el
 * servidor lo compara con su propio cálculo (CHK-004).
 */
public record CheckoutRequest(
        @NotBlank @Email @Size(max = 255) String correoCliente,
        @NotBlank @Size(max = 150) String nombreCliente,
        @Size(max = 30) String telefonoCliente,
        @Valid DireccionCheckoutRequest direccionEnvio,
        @Valid DireccionCheckoutRequest direccionFacturacion,
        UUID tarifaEnvioId,
        @NotNull PasarelaPago pasarela,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal totalEsperado) {
}
