package shopify.api.core.modules.tienda.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.BaseInputDto;

@Getter
@Setter
public class ActualizarTiendaRequest extends BaseInputDto {

    @NotBlank(message = "El nombre de la tienda es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @Size(max = 150, message = "El dominio personalizado no puede superar los 150 caracteres")
    private String dominioPersonalizado;

    @Size(max = 3, message = "La moneda debe tener 3 caracteres ISO")
    private String moneda;

    @NotBlank(message = "El correo de contacto es obligatorio")
    @Email(message = "El formato de correo no es válido")
    private String correoContacto;

    private Boolean estaActiva;
}