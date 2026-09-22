package shopify.api.core.modules.tienda.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.BaseInputDto;

@Getter
@Setter
public class CrearTiendaRequest extends BaseInputDto {

    @NotBlank(message = "El nombre de la tienda es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @NotBlank(message = "El subdominio es obligatorio")
    @Size(min = 3, max = 50, message = "El subdominio debe tener entre 3 y 50 caracteres")
    @Pattern(regexp = "^[a-z0-9-]+$", message = "El subdominio solo puede contener letras minúsculas, números y guiones")
    private String subdominio;

    @Size(max = 3, message = "La moneda debe tener 3 caracteres ISO (ej: USD, PEN)")
    private String moneda = "USD";

    @NotBlank(message = "El correo de contacto es obligatorio")
    @Email(message = "El formato de correo no es válido")
    private String correoContacto;
}