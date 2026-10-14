package shopify.api.core.modulos.catalogo.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.CategoriaEntidad;

public interface CategoriaRepositorio extends JpaRepository<CategoriaEntidad, UUID> {

    Optional<CategoriaEntidad> findByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    List<CategoriaEntidad> findByTiendaIdAndEliminadoFalseOrderByNombreAsc(UUID tiendaId);

    List<CategoriaEntidad> findByIdInAndTiendaIdAndEliminadoFalse(Collection<UUID> ids, UUID tiendaId);

    boolean existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(UUID tiendaId, String identificadorUrl);

    boolean existsByTiendaIdAndIdentificadorUrlAndEliminadoFalseAndIdNot(UUID tiendaId, String identificadorUrl, UUID id);

    /** Categorías con al menos un producto publicado (vitrina, CAT-011). */
    @Query("""
            SELECT DISTINCT c FROM CategoriaEntidad c, ProductoCategoriaEntidad pc, ProductoEntidad p
            WHERE pc.categoriaId = c.id AND pc.productoId = p.id
              AND c.tiendaId = :tiendaId AND c.eliminado = FALSE
              AND pc.eliminado = FALSE AND p.eliminado = FALSE AND p.estaPublicado = TRUE
            ORDER BY c.nombre
            """)
    List<CategoriaEntidad> conProductosPublicados(@Param("tiendaId") UUID tiendaId);
}
