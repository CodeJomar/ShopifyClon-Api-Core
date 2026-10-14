package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearTiendaRequest(
        @NotBlank(message = "El nombre de la tienda es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String nombre,

        @NotBlank(message = "El subdominio es obligatorio")
        @Size(max = 63, message = "El subdominio no puede superar los 63 caracteres")
        String subdominio,

        @NotBlank(message = "El correo de contacto es obligatorio")
        @Email(message = "El formato de correo no es válido")
        @Size(max = 255, message = "El correo no puede superar los 255 caracteres")
        String correoContacto,

        @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe ser un código de 3 letras mayúsculas")
        String moneda,

        @Size(max = 30, message = "El código de plan no puede superar los 30 caracteres")
        String codigoPlan) {
}
