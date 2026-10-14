package shopify.api.core.modulos.envio.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Alta y edición de una tarifa de envío. En la edición, {@code esta_activa} ausente conserva su valor
 * y {@code orden} ausente equivale a 0.
 */
public record TarifaEnvioRequest(
        @NotBlank(message = "El nombre de la tarifa es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        String descripcion,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo")
        BigDecimal precio,

        @DecimalMin(value = "0", message = "El monto mínimo para envío gratis no puede ser negativo")
        BigDecimal montoMinimoGratis,

        Boolean estaActiva,

        Integer orden) {
}
