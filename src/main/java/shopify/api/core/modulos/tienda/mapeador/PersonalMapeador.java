package shopify.api.core.modulos.tienda.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import shopify.api.core.modulos.tienda.dto.response.MiembroPersonalResponse;
import shopify.api.core.modulos.tienda.modelo.MiembroPersonalVista;

@Mapper(componentModel = "spring")
public interface PersonalMapeador {

    MiembroPersonalResponse aResponse(MiembroPersonalVista vista);

    List<MiembroPersonalResponse> aResponses(List<MiembroPersonalVista> vistas);
}
