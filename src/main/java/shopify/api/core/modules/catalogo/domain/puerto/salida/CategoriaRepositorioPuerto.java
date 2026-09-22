package shopify.api.core.modules.catalogo.domain.puerto.salida;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;

import java.util.Optional;
import java.util.UUID;

public interface CategoriaRepositorioPuerto {
    Categoria guardar(Categoria categoria);
    Optional<Categoria> buscarPorId(UUID id);
    Optional<Categoria> buscarPorSlugYTiendaId(String slug, UUID tiendaId);
    boolean existePorNombreYTiendaId(String nombre, UUID tiendaId);
    Page<Categoria> buscarConFiltros(UUID tiendaId, String textoBusqueda, Pageable pageable);
    void eliminarLogico(UUID id);
}