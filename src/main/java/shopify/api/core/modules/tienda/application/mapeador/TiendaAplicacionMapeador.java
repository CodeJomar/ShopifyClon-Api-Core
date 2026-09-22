package shopify.api.core.modules.tienda.application.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modules.tienda.application.dto.response.TiendaResponse;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TiendaAplicacionMapeador {
    TiendaResponse aResponse(Tienda tienda);
}