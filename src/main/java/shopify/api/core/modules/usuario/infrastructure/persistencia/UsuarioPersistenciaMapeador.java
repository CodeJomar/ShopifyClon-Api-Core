package shopify.api.core.modules.usuario.infrastructure.persistencia;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import shopify.api.core.modules.usuario.domain.modelo.Rol;
import shopify.api.core.modules.usuario.domain.modelo.Usuario;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.RolEntidad;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;

@Mapper(componentModel = "spring")
public interface UsuarioPersistenciaMapeador {

    Usuario aDominio(UsuarioEntidad entidad);

    UsuarioEntidad aEntidad(Usuario modelo);

    Rol aDominioRol(RolEntidad entidad);

    RolEntidad aEntidadRol(Rol modelo);
}