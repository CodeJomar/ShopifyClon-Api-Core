package shopify.api.core.modules.catalogo.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.BaseInputDto;

@Getter
@Setter
public class ActualizarCategoriaRequest extends BaseInputDto {

    @NotBlank(message = "El nombre de la categoría es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    private String descripcion;

    @Size(max = 500, message = "La URL de la imagen no puede superar los 500 caracteres")
    private String urlImagen;

    private Boolean estaActiva;
}