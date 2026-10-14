package shopify.api.core.modulos.usuario.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import shopify.api.core.modulos.usuario.dto.request.DireccionRequest;
import shopify.api.core.modulos.usuario.dto.response.DireccionResponse;
import shopify.api.core.modulos.usuario.entidad.DireccionUsuarioEntidad;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DireccionMapeador {

    DireccionResponse aResponse(DireccionUsuarioEntidad direccion);

    List<DireccionResponse> aResponses(List<DireccionUsuarioEntidad> direcciones);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "esPrincipal", ignore = true)
    DireccionUsuarioEntidad aEntidad(DireccionRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "usuarioId", ignore = true)
    @Mapping(target = "esPrincipal", ignore = true)
    void actualizar(DireccionRequest request, @MappingTarget DireccionUsuarioEntidad direccion);
}
