package shopify.api.core.modules.catalogo.infrastructure.persistencia.adaptador;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import shopify.api.core.modules.catalogo.domain.modelo.Categoria;
import shopify.api.core.modules.catalogo.domain.puerto.salida.CategoriaRepositorioPuerto;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.CategoriaPersistenciaMapeador;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.CategoriaEntidad;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio.CategoriaJpaRepositorio;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CategoriaRepositorioAdaptador implements CategoriaRepositorioPuerto {

    private final CategoriaJpaRepositorio jpaRepositorio;
    private final CategoriaPersistenciaMapeador mapeador;

    @Override
    public Categoria guardar(Categoria categoria) {
        CategoriaEntidad entidad = mapeador.aEntidad(categoria);
        CategoriaEntidad guardada = jpaRepositorio.save(entidad);
        return mapeador.aDominio(guardada);
    }

    @Override
    public Optional<Categoria> buscarPorId(UUID id) {
        return jpaRepositorio.findByIdAndEliminadoFalse(id)
                .map(mapeador::aDominio);
    }

    @Override
    public Optional<Categoria> buscarPorSlugYTiendaId(String slug, UUID tiendaId) {
        return jpaRepositorio.findBySlugAndTiendaIdAndEliminadoFalse(slug, tiendaId)
                .map(mapeador::aDominio);
    }

    @Override
    public boolean existePorNombreYTiendaId(String nombre, UUID tiendaId) {
        return jpaRepositorio.existsByNombreIgnoreCaseAndTiendaIdAndEliminadoFalse(nombre, tiendaId);
    }

    @Override
    public Page<Categoria> buscarConFiltros(UUID tiendaId, String textoBusqueda, Pageable pageable) {
        return jpaRepositorio.buscarPaginado(tiendaId, textoBusqueda, pageable)
                .map(mapeador::aDominio);
    }

    @Override
    public void eliminarLogico(UUID id) {
        jpaRepositorio.findByIdAndEliminadoFalse(id).ifPresent(entidad -> {
            entidad.setEliminado(true);
            jpaRepositorio.save(entidad);
        });
    }
}