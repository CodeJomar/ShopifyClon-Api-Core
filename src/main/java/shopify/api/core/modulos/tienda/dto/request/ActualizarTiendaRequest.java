package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ActualizarTiendaRequest(
        @NotBlank(message = "El nombre de la tienda es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El correo de contacto es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoContacto,

        @Size(max = 150, message = "El dominio no puede superar los 150 caracteres")
        String dominioPersonalizado,

        @NotBlank(message = "La moneda es obligatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe ser un código de 3 letras mayúsculas")
        String moneda,

        @NotNull(message = "La tasa de impuesto es obligatoria")
        @DecimalMin(value = "0", message = "La tasa de impuesto no puede ser negativa")
        @DecimalMax(value = "0.50", message = "La tasa de impuesto no puede superar 0.50")
        @Digits(integer = 1, fraction = 4, message = "La tasa de impuesto admite hasta 4 decimales")
        BigDecimal tasaImpuesto,

        @NotNull(message = "Indica si los precios incluyen impuesto")
        Boolean preciosIncluyenImpuesto,

        @NotBlank(message = "La zona horaria es obligatoria")
        @Size(max = 50, message = "La zona horaria no puede superar los 50 caracteres")
        String zonaHoraria) {
}
