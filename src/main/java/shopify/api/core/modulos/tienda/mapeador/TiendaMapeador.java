package shopify.api.core.modulos.tienda.mapeador;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.tienda.dto.response.MiTiendaResponse;
import shopify.api.core.modulos.tienda.dto.response.SuscripcionResumenResponse;
import shopify.api.core.modulos.tienda.dto.response.AparienciaResponse;
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

    @Mapping(target = "apariencia", source = "tienda")
    TiendaConfiguracionResponse aConfiguracion(TiendaEntidad tienda, boolean operativa, SuscripcionResumenResponse suscripcion);

    @Mapping(target = "apariencia", source = "tienda")
    TiendaVitrinaResponse aVitrina(TiendaEntidad tienda);

    default AparienciaResponse aApariencia(TiendaEntidad tienda) {
        return new AparienciaResponse(tienda.getPaleta(), tienda.getTipografia(), tienda.getTamanoLetra(), tienda.getDiseno(),
                tienda.getModo(), tienda.getUrlLogo());
    }
}
