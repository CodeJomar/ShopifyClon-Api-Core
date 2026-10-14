package shopify.api.core.modulos.usuario.mapeador;

import java.util.List;
import org.mapstruct.Mapper;
import shopify.api.core.modulos.usuario.dto.response.PerfilResponse;
import shopify.api.core.modulos.usuario.dto.response.TiendaDeUsuarioResponse;
import shopify.api.core.modulos.usuario.entidad.UsuarioEntidad;
import shopify.api.core.modulos.usuario.modelo.TiendaDeUsuarioVista;

@Mapper(componentModel = "spring")
public interface UsuarioMapeador {

    PerfilResponse aPerfil(UsuarioEntidad usuario);

    TiendaDeUsuarioResponse aTienda(TiendaDeUsuarioVista vista);

    List<TiendaDeUsuarioResponse> aTiendas(List<TiendaDeUsuarioVista> vistas);
}
