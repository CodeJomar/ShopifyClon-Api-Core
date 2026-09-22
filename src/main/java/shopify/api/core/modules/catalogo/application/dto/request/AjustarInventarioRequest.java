package shopify.api.core.modules.catalogo.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AjustarInventarioRequest {

    @NotNull(message = "La nueva cantidad disponible es obligatoria")
    private Integer nuevaCantidadDisponible;
}