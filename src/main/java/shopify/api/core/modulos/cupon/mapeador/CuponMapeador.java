package shopify.api.core.modulos.cupon.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.cupon.dto.response.CuponResponse;
import shopify.api.core.modulos.cupon.entidad.CuponEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CuponMapeador {

    CuponResponse aResponse(CuponEntidad cupon);
}
