package shopify.api.core.modulos.checkout.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Dirección de envío o de facturación. El teléfono es opcional; el resto de campos son obligatorios. */
public record DireccionCheckoutRequest(
        @NotBlank @Size(max = 150) String nombreContacto,
        @Size(max = 30) String telefono,
        @NotBlank @Size(max = 200) String direccionLinea1,
        @Size(max = 200) String direccionLinea2,
        @NotBlank @Size(max = 100) String ciudad,
        @NotBlank @Size(max = 100) String estadoProvincia,
        @NotBlank @Size(max = 20) String codigoPostal,
        @NotBlank @Size(max = 60) String pais) {
}
