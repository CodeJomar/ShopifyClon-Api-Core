package shopify.api.core.modules.usuario.application.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import shopify.api.core.modules.usuario.application.dto.response.UsuarioResponse;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;

@Mapper(componentModel = "spring")
public interface UsuarioAplicacionMapeador {

    @Mapping(target = "rol", source = "rol.codigo")
    UsuarioResponse aResponse(Usuario usuario);
}