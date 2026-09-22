package shopify.api.core.modules.catalogo.application.dto.request;

import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.PaginacionQueryInput;

@Getter
@Setter
public class ProductoQueryInput extends PaginacionQueryInput {
    private Boolean estaPublicado;
}