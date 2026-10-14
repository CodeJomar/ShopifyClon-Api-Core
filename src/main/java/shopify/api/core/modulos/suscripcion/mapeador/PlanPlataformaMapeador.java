package shopify.api.core.modulos.suscripcion.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.suscripcion.dto.response.PlanPlataformaResponse;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanPlataformaMapeador {

    PlanPlataformaResponse aResponse(PlanSuscripcionEntidad plan);

    List<PlanPlataformaResponse> aResponses(List<PlanSuscripcionEntidad> planes);
}
