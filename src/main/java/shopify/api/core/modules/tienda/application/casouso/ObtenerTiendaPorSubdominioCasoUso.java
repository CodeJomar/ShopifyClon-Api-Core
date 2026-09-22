package shopify.api.core.modules.tienda.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.tienda.application.dto.response.TiendaResponse;
import shopify.api.core.modules.tienda.application.mapeador.TiendaAplicacionMapeador;
import shopify.api.core.modules.tienda.domain.modelo.Tienda;
import shopify.api.core.modules.tienda.domain.puerto.salida.TiendaRepositorioPuerto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ObtenerTiendaPorSubdominioCasoUso {

    private final TiendaRepositorioPuerto tiendaRepositorio;
    private final TiendaAplicacionMapeador mapeador;

    @Transactional(readOnly = true)
    public TiendaResponse ejecutar(String subdominio) {
        String subdominioLimpio = subdominio.trim().toLowerCase(Locale.ROOT);

        Tienda tienda = tiendaRepositorio.buscarPorSubdominio(subdominioLimpio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró ninguna tienda con el subdominio: " + subdominioLimpio));

        return mapeador.aResponse(tienda);
    }
}