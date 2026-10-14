package shopify.api.core.modulos.suscripcion.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Alta de un plan (administración de la plataforma). Los rangos son los CHECK de V2; {@code es_publico} por defecto true y {@code orden} por defecto 0. */
public record CrearPlanRequest(
        @NotBlank(message = "El código es obligatorio")
        @Pattern(regexp = "^[A-Z_]{3,30}$", message = "El código debe tener de 3 a 30 letras mayúsculas o guiones bajos")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @NotNull(message = "El precio mensual es obligatorio")
        @DecimalMin(value = "0", message = "El precio mensual no puede ser negativo")
        BigDecimal precioMensual,

        @NotNull(message = "El precio anual es obligatorio")
        @DecimalMin(value = "0", message = "El precio anual no puede ser negativo")
        BigDecimal precioAnual,

        @NotBlank(message = "La moneda es obligatoria")
        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe ser un código de 3 letras mayúsculas")
        String moneda,

        @Min(value = 1, message = "El límite de productos debe ser al menos 1 (vacío = ilimitado)")
        Integer limiteProductos,

        @NotNull(message = "El límite de personal es obligatorio")
        @Min(value = 0, message = "El límite de personal no puede ser negativo")
        Integer limitePersonal,

        @NotNull(message = "La comisión es obligatoria")
        @DecimalMin(value = "0", message = "La comisión no puede ser negativa")
        @DecimalMax(value = "0.10", message = "La comisión no puede superar el 10 %")
        BigDecimal porcentajeComision,

        @Min(value = 0, message = "Los días de prueba no pueden ser negativos")
        Integer diasPrueba,

        Boolean esPublico,

        Integer orden) {
}
