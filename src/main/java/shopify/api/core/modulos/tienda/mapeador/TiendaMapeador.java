package shopify.api.core.modulos.tienda.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.tienda.dto.response.MiTiendaResponse;
import shopify.api.core.modulos.tienda.dto.response.SuscripcionResumenResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaConfiguracionResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaCreadaResponse;
import shopify.api.core.modulos.tienda.dto.response.TiendaVitrinaResponse;
import shopify.api.core.modulos.tienda.entidad.TiendaEntidad;
import shopify.api.core.modulos.tienda.modelo.MiTiendaVista;

@Mapper(componentModel = "spring")
public interface TiendaMapeador {

    @Mapping(target = "estado", expression = "java(vigente.estado().name())")
    SuscripcionResumenResponse aResumen(SuscripcionVigente vigente);

    TiendaCreadaResponse aCreada(TiendaEntidad tienda, SuscripcionResumenResponse suscripcion, boolean operativa);

    MiTiendaResponse aMiTienda(MiTiendaVista vista, boolean operativa);

    TiendaConfiguracionResponse aConfiguracion(TiendaEntidad tienda, boolean operativa, SuscripcionResumenResponse suscripcion);

    TiendaVitrinaResponse aVitrina(TiendaEntidad tienda);
}
