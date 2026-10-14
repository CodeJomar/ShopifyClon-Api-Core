package shopify.api.core.modulos.suscripcion.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import shopify.api.core.modulos.suscripcion.modelo.CicloFacturacion;

public record CambiarPlanSuscripcionRequest(
        @NotBlank(message = "El código de plan es obligatorio")
        @Size(max = 30, message = "El código de plan no puede superar los 30 caracteres")
        String codigoPlan,

        @NotNull(message = "El ciclo de facturación es obligatorio")
        CicloFacturacion ciclo,

        @NotBlank(message = "El token de pago es obligatorio")
        String tokenPago) {
}
