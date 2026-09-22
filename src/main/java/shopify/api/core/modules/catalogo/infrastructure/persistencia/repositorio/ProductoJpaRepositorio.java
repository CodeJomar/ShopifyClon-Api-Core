package shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoEntidad;

import java.util.Optional;
import java.util.UUID;

public interface ProductoJpaRepositorio extends JpaRepository<ProductoEntidad, UUID> {

    Optional<ProductoEntidad> findByIdAndEliminadoFalse(UUID id);

    boolean existsByIdentificadorUrlAndTiendaIdAndEliminadoFalse(String identificadorUrl, UUID tiendaId);

    @Query("""
        SELECT p FROM ProductoEntidad p 
        WHERE p.eliminado = false 
          AND (:tiendaId IS NULL OR p.tiendaId = :tiendaId)
          AND (:estaPublicado IS NULL OR p.estaPublicado = :estaPublicado)
          AND (:busqueda IS NULL OR LOWER(p.titulo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                                 OR LOWER(p.proveedor) LIKE LOWER(CONCAT('%', :busqueda, '%')))
    """)
    Page<ProductoEntidad> buscarConFiltros(
            @Param("tiendaId") UUID tiendaId,
            @Param("estaPublicado") Boolean estaPublicado,
            @Param("busqueda") String busqueda,
            Pageable pageable);
}