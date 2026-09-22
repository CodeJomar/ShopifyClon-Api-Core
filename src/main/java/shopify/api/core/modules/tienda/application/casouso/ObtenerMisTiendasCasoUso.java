package shopify.api.core.modules.tienda.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.tienda.application.dto.response.TiendaResponse;
import shopify.api.core.modules.tienda.application.mapeador.TiendaAplicacionMapeador;
import shopify.api.core.modules.tienda.domain.puerto.salida.TiendaRepositorioPuerto;
import shopify.api.core.shared.seguridad.servicio.SeguridadUtil;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObtenerMisTiendasCasoUso {

    private final TiendaRepositorioPuerto tiendaRepositorio;
    private final TiendaAplicacionMapeador mapeador;

    @Transactional(readOnly = true)
    public List<TiendaResponse> ejecutar() {
        UUID propietarioId = SeguridadUtil.obtenerUsuarioIdActual();
        return tiendaRepositorio.buscarPorPropietarioId(propietarioId).stream()
                .map(mapeador::aResponse)
                .toList();
    }
}