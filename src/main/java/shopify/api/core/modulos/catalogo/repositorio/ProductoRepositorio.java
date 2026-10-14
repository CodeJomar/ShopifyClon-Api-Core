package shopify.api.core.modulos.catalogo.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.ProductoEntidad;

public interface ProductoRepositorio extends JpaRepository<ProductoEntidad, UUID> {

    Optional<ProductoEntidad> findByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    boolean existsByTiendaIdAndIdentificadorUrlAndEliminadoFalse(UUID tiendaId, String identificadorUrl);

    boolean existsByTiendaIdAndIdentificadorUrlAndEliminadoFalseAndIdNot(UUID tiendaId, String identificadorUrl, UUID id);

    /** Panel: {@code patron}, {@code publicado} y {@code categoriaId} son opcionales (nulos no filtran). */
    @Query("""
            SELECT p FROM ProductoEntidad p
            WHERE p.tiendaId = :tiendaId AND p.eliminado = FALSE
              AND (:publicado IS NULL OR p.estaPublicado = :publicado)
              AND (:patron IS NULL OR LOWER(p.titulo) LIKE :patron ESCAPE '!')
              AND (:categoriaId IS NULL OR EXISTS (
                    SELECT pc FROM ProductoCategoriaEntidad pc
                    WHERE pc.productoId = p.id AND pc.categoriaId = :categoriaId AND pc.eliminado = FALSE))
            """)
    Page<ProductoEntidad> buscarPanel(@Param("tiendaId") UUID tiendaId,
                                      @Param("patron") String patron,
                                      @Param("publicado") Boolean publicado,
                                      @Param("categoriaId") UUID categoriaId,
                                      Pageable pagina);

    /** Vitrina: solo publicados; la categoría se filtra por su slug dentro de la misma tienda. */
    @Query("""
            SELECT p FROM ProductoEntidad p
            WHERE p.tiendaId = :tiendaId AND p.eliminado = FALSE AND p.estaPublicado = TRUE
              AND (:patron IS NULL OR LOWER(p.titulo) LIKE :patron ESCAPE '!')
              AND (:categoriaSlug IS NULL OR EXISTS (
                    SELECT pc FROM ProductoCategoriaEntidad pc, CategoriaEntidad c
                    WHERE pc.productoId = p.id AND pc.categoriaId = c.id AND pc.eliminado = FALSE
                      AND c.tiendaId = :tiendaId AND c.identificadorUrl = :categoriaSlug AND c.eliminado = FALSE))
            """)
    List<ProductoEntidad> buscarPublicados(@Param("tiendaId") UUID tiendaId,
                                           @Param("patron") String patron,
                                           @Param("categoriaSlug") String categoriaSlug);

    Optional<ProductoEntidad> findByTiendaIdAndIdentificadorUrlAndEstaPublicadoTrueAndEliminadoFalse(UUID tiendaId,
                                                                                                    String identificadorUrl);
}
