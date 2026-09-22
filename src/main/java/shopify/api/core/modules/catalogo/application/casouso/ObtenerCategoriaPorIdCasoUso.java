package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.response.CategoriaResponse;
import shopify.api.core.modules.catalogo.application.mapeador.CategoriaAplicacionMapeador;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ObtenerCategoriaPorIdCasoUso {

    private final CategoriaRepositorioPuerto categoriaRepositorio;
    private final CategoriaAplicacionMapeador mapeador;

    @Transactional(readOnly = true)
    public CategoriaResponse ejecutar(UUID id) {
        Categoria categoria = categoriaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la categoría con ID: " + id));

        return mapeador.aResponse(categoria);
    }
}