package shopify.api.core.modules.catalogo.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.BaseInputDto;

@Getter
@Setter
public class ActualizarProductoRequest extends BaseInputDto {

    @NotBlank(message = "El título del producto es obligatorio")
    @Size(max = 200, message = "El título no puede superar los 200 caracteres")
    private String titulo;

    private String descripcion;
    private String proveedor;
    private Boolean esDigital;
    private Boolean estaPublicado;
}