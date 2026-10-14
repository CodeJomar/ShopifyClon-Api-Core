package shopify.api.core.modulos.envio.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.envio.dto.response.TarifaEnvioResponse;
import shopify.api.core.modulos.envio.dto.response.TarifaVitrinaResponse;
import shopify.api.core.modulos.envio.entidad.TarifaEnvioEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TarifaEnvioMapeador {

    TarifaEnvioResponse aResponse(TarifaEnvioEntidad tarifa);

    List<TarifaEnvioResponse> aResponses(List<TarifaEnvioEntidad> tarifas);

    TarifaVitrinaResponse aVitrina(TarifaEnvioEntidad tarifa);

    List<TarifaVitrinaResponse> aVitrinas(List<TarifaEnvioEntidad> tarifas);
}
