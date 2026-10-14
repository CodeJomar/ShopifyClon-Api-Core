package shopify.api.core.modulos.suscripcion.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.suscripcion.dto.response.PlanResponse;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanMapeador {

    PlanResponse aResponse(PlanSuscripcionEntidad plan);

    List<PlanResponse> aResponses(List<PlanSuscripcionEntidad> planes);
}
