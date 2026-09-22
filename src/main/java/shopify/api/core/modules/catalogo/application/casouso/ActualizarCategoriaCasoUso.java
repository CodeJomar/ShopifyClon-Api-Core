package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.application.dto.request.ActualizarCategoriaRequest;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ActualizarCategoriaCasoUso {

    private final CategoriaRepositorioPuerto categoriaRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID id, ActualizarCategoriaRequest request) {
        Categoria categoria = categoriaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada con ID: " + id));

        categoria.setNombre(request.getNombre().trim());
        categoria.setDescripcion(request.getDescripcion());
        categoria.setUrlImagen(request.getUrlImagen());

        if (request.getEstaActiva() != null) {
            categoria.setEstaActiva(request.getEstaActiva());
        }

        categoriaRepositorio.guardar(categoria);

        return EstadoOperacionDto.exitoso(id, "Categoría actualizada exitosamente");
    }
}