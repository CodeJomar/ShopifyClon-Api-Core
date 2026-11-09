package shopify.api.core.modulos.checkout.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dirección de envío o de facturación. El teléfono es opcional; el resto de campos son obligatorios.
 * {@code latitud} y {@code longitud} (punto de entrega en el mapa) son obligatorias en la dirección de envío
 * cuando alguna línea requiere envío (ENV-007); aquí son opcionales porque la factura no las necesita.
 */
public record DireccionCheckoutRequest(
        @NotBlank @Size(max = 150) String nombreContacto,
        @Size(max = 30) String telefono,
        @NotBlank @Size(max = 200) String direccionLinea1,
        @Size(max = 200) String direccionLinea2,
        @NotBlank @Size(max = 100) String ciudad,
        @NotBlank @Size(max = 100) String estadoProvincia,
        @NotBlank @Size(max = 20) String codigoPostal,
        @NotBlank @Size(max = 60) String pais,
        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90") BigDecimal latitud,
        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180") BigDecimal longitud) {
}
