package shopify.api.core.modulos.cupon.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;

/**
 * Alta y edición de un cupón. En la edición, {@code fecha_inicio} y {@code esta_activo} ausentes
 * conservan su valor; {@code monto_minimo_compra} ausente equivale a 0 y {@code limite_usos_total} ausente, a sin límite.
 */
public record CuponRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 50, message = "El código no puede superar los 50 caracteres")
        String codigo,

        @NotNull(message = "El tipo de descuento es obligatorio")
        TipoDescuento tipoDescuento,

        @NotNull(message = "El valor del descuento es obligatorio")
        @DecimalMin(value = "0", message = "El valor del descuento no puede ser negativo")
        BigDecimal valorDescuento,

        @DecimalMin(value = "0", message = "El monto mínimo no puede ser negativo")
        BigDecimal montoMinimoCompra,

        @Min(value = 1, message = "El límite de usos debe ser al menos 1")
        Integer limiteUsosTotal,

        Instant fechaInicio,

        Instant fechaFin,

        Boolean estaActivo) {
}
