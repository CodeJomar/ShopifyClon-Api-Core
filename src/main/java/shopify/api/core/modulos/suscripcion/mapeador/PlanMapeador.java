package shopify.api.core.modulos.suscripcion.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.suscripcion.dto.response.PlanResponse;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PlanMapeador {

    @Mapping(target = "esGratuito", expression = "java(plan.esGratuito())")
    PlanResponse aResponse(PlanSuscripcionEntidad plan);

    List<PlanResponse> aResponses(List<PlanSuscripcionEntidad> planes);
}
