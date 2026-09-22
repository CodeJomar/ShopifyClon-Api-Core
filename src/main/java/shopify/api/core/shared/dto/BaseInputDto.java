package shopify.api.core.shared.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public abstract class BaseInputDto {

    /**
     * Se ignora en el JSON de entrada porque el backend lo extrae de forma
     * segura desde el token JWT (SecurityContext) en el controlador.
     */
    @JsonIgnore
    private UUID idUsuario;

    @JsonIgnore
    private UUID idTienda;
}