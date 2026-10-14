package shopify.api.core.modulos.pago.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Motivo de cancelación (ORD-007). El detalle es obligatorio cuando el motivo es OTRO; eso lo valida el servicio. */
public record CancelarOrdenRequest(
        @NotBlank @Pattern(regexp = "CLIENTE|FRAUDE|SIN_STOCK|OTRO") String motivo,
        @Size(max = 255) String detalle) {
}
