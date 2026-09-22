package shopify.api.core.modules.tienda.infrastructure.persistencia;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;
import shopify.api.core.modules.tienda.infrastructure.persistencia.entidad.TiendaEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TiendaPersistenciaMapeador {
    Tienda aDominio(TiendaEntidad entidad);
    TiendaEntidad aEntidad(Tienda modelo);
}