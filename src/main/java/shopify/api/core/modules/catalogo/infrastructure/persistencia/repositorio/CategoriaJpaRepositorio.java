package shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.CategoriaEntidad;

import java.util.Optional;
import java.util.UUID;

public interface CategoriaJpaRepositorio extends JpaRepository<CategoriaEntidad, UUID> {

    Optional<CategoriaEntidad> findByIdAndEliminadoFalse(UUID id);

    Optional<CategoriaEntidad> findBySlugAndTiendaIdAndEliminadoFalse(String slug, UUID tiendaId);

    boolean existsByNombreIgnoreCaseAndTiendaIdAndEliminadoFalse(String nombre, UUID tiendaId);

    @Query("""
        SELECT c FROM CategoriaEntidad c 
        WHERE c.eliminado = false 
          AND (:tiendaId IS NULL OR c.tiendaId = :tiendaId)
          AND (:busqueda IS NULL OR LOWER(c.nombre) LIKE LOWER(CONCAT('%', :busqueda, '%')))
    """)
    Page<CategoriaEntidad> buscarPaginado(
            @Param("tiendaId") UUID tiendaId,
            @Param("busqueda") String busqueda,
            Pageable pageable);
}