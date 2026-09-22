package shopify.api.core.modules.catalogo.application.casouso;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.shared.dto.EstadoOperacionDto;
import shopify.api.core.shared.exception.RecursoNoEncontradoException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EliminarCategoriaCasoUso {

    private final CategoriaRepositorioPuerto categoriaRepositorio;

    @Transactional
    public EstadoOperacionDto ejecutar(UUID id) {
        categoriaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Categoría no encontrada con ID: " + id));

        categoriaRepositorio.eliminarLogico(id);

        return EstadoOperacionDto.exitoso(id, "Categoría eliminada exitosamente");
    }
}