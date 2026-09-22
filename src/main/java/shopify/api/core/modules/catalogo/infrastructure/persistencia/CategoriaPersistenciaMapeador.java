package shopify.api.core.modules.catalogo.infrastructure.persistencia;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.CategoriaEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CategoriaPersistenciaMapeador {
    Categoria aDominio(CategoriaEntidad entidad);
    CategoriaEntidad aEntidad(Categoria modelo);
}