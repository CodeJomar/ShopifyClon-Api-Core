package shopify.api.core.modulos.usuario.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Datos de una dirección; {@code esPrincipal} solo se usa al crear. */
public record DireccionRequest(
        @NotBlank(message = "El nombre de contacto es obligatorio")
        @Size(max = 150, message = "El nombre de contacto no puede superar los 150 caracteres")
        String nombreContacto,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String telefono,

        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String direccionLinea1,

        @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
        String direccionLinea2,

        @NotBlank(message = "La ciudad es obligatoria")
        @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres")
        String ciudad,

        @NotBlank(message = "El estado o provincia es obligatorio")
        @Size(max = 100, message = "El estado o provincia no puede superar los 100 caracteres")
        String estadoProvincia,

        @NotBlank(message = "El código postal es obligatorio")
        @Size(max = 20, message = "El código postal no puede superar los 20 caracteres")
        String codigoPostal,

        @NotBlank(message = "El país es obligatorio")
        @Size(max = 100, message = "El país no puede superar los 100 caracteres")
        String pais,

        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
        BigDecimal latitud,

        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
        BigDecimal longitud,

        Boolean esPrincipal) {
}
