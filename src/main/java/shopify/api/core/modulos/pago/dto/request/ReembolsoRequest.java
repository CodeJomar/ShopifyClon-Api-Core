package shopify.api.core.modulos.pago.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Reembolso parcial o total (PAG-007). La reposición de stock es opcional. */
public record ReembolsoRequest(
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal monto,
        @NotBlank @Size(min = 5, max = 255) String motivo,
        @Valid List<LineaReposicionRequest> reponer) {

    public record LineaReposicionRequest(@NotNull UUID varianteId, @Positive int cantidad) {
    }
}
