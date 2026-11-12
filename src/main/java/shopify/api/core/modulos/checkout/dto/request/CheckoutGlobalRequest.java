package shopify.api.core.modulos.checkout.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;

/**
 * Checkout global (R-001): un pedido con una orden hija por tienda del carrito. {@code totalEsperado} es el total
 * general que vio el comprador; el servidor lo compara con su cálculo (CHK-004).
 */
public record CheckoutGlobalRequest(
        @NotBlank @Email @Size(max = 255) String correoCliente,
        @NotBlank @Size(max = 150) String nombreCliente,
        @Size(max = 30) String telefonoCliente,
        @Valid DireccionCheckoutRequest direccionEnvio,
        @Valid DireccionCheckoutRequest direccionFacturacion,
        @Size(max = 50) List<@Valid EnvioElegido> envios,
        @NotNull PasarelaPago pasarela,
        @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal totalEsperado) {

    public record EnvioElegido(@NotNull UUID tiendaId, @NotNull UUID tarifaEnvioId) {
    }
}
