package shopify.api.core.modules.catalogo.application.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modules.catalogo.application.dto.response.CategoriaResponse;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoriaAplicacionMapeador {
    CategoriaResponse aResponse(Categoria categoria);
}