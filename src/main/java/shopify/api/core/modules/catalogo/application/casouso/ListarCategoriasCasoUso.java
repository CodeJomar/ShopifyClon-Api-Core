package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.CategoriaQueryInput;
import shopify.api.core.modules.catalogo.application.dto.response.CategoriaResponse;
import shopify.api.core.modules.catalogo.application.mapeador.CategoriaAplicacionMapeador;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.shared.dto.ConsultaPaginadaDto;
import shopify.api.core.shared.multitenancy.TenantContextHolder;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListarCategoriasCasoUso {

    private final CategoriaRepositorioPuerto categoriaRepositorio;
    private final CategoriaAplicacionMapeador mapeador;

    @Transactional(readOnly = true)
    public ConsultaPaginadaDto<CategoriaResponse> ejecutar(CategoriaQueryInput input) {
        UUID tiendaId = TenantContextHolder.getTenantId();

        Sort.Direction direccion = "ASC".equalsIgnoreCase(input.getDireccionOrden())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        PageRequest pageRequest = PageRequest.of(
                input.getPagina(),
                input.getTamano(),
                Sort.by(direccion, "fechaCreacion")
        );

        Page<CategoriaResponse> pagina = categoriaRepositorio
                .buscarConFiltros(tiendaId, input.getTextoBusqueda(), pageRequest)
                .map(mapeador::aResponse);

        return ConsultaPaginadaDto.desdePagina(pagina);
    }
}