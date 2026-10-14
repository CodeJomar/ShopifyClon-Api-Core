package shopify.api.core.modulos.catalogo.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;

public interface VarianteProductoRepositorio extends JpaRepository<VarianteProductoEntidad, UUID> {

    List<VarianteProductoEntidad> findByProductoIdAndEliminadoFalseOrderByFechaCreacionAsc(UUID productoId);

    List<VarianteProductoEntidad> findByProductoIdInAndEliminadoFalse(Collection<UUID> productoIds);

    Optional<VarianteProductoEntidad> findByIdAndProductoIdAndEliminadoFalse(UUID id, UUID productoId);

    long countByProductoIdAndEliminadoFalse(UUID productoId);

    /** Variante por id solo si su producto es de la tienda (SEG-004: otra tienda no la ve). */
    @Query("""
            SELECT v FROM VarianteProductoEntidad v, ProductoEntidad p
            WHERE v.id = :id AND v.productoId = p.id AND p.tiendaId = :tiendaId
              AND v.eliminado = FALSE AND p.eliminado = FALSE
            """)
    Optional<VarianteProductoEntidad> buscarDeTienda(@Param("id") UUID id, @Param("tiendaId") UUID tiendaId);

    /** SKU ya usado por otra variante no eliminada de la misma tienda (CAT-003). {@code excluirId} nulo no excluye nada. */
    @Query("""
            SELECT COUNT(v) > 0 FROM VarianteProductoEntidad v, ProductoEntidad p
            WHERE v.productoId = p.id AND p.tiendaId = :tiendaId
              AND UPPER(v.sku) = :sku AND v.eliminado = FALSE AND p.eliminado = FALSE
              AND (:excluirId IS NULL OR v.id <> :excluirId)
            """)
    boolean existeSkuEnTienda(@Param("tiendaId") UUID tiendaId, @Param("sku") String sku,
                              @Param("excluirId") UUID excluirId);
}
