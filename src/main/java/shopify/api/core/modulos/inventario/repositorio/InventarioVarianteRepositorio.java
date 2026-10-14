package shopify.api.core.modulos.inventario.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.inventario.entidad.InventarioVarianteEntidad;

public interface InventarioVarianteRepositorio extends JpaRepository<InventarioVarianteEntidad, UUID> {

    Optional<InventarioVarianteEntidad> findFirstByVarianteIdAndEliminadoFalse(UUID varianteId);

    List<InventarioVarianteEntidad> findByVarianteIdInAndEliminadoFalse(Collection<UUID> varianteIds);

    /**
     * INV-009: bloquea en una sola consulta las filas de inventario de la tienda, ordenadas por {@code VarianteId}.
     * {@code FOR UPDATE OF i} bloquea solo la fila de inventario, no la variante ni el producto. Nativa porque
     * la restricción a la tienda necesita unir variante y producto.
     */
    @Query(value = """
            SELECT i.* FROM "InventarioVariante" i
            JOIN "VariantesProducto" v ON v."Id" = i."VarianteId"
            JOIN "Productos" p ON p."Id" = v."ProductoId"
            WHERE i."VarianteId" IN (:varianteIds) AND p."TiendaId" = :tiendaId
              AND i."Eliminado" = FALSE AND v."Eliminado" = FALSE AND p."Eliminado" = FALSE
            ORDER BY i."VarianteId"
            FOR UPDATE OF i
            """, nativeQuery = true)
    List<InventarioVarianteEntidad> bloquearPorVariantes(@Param("tiendaId") UUID tiendaId,
                                                        @Param("varianteIds") Collection<UUID> varianteIds);

    /** Lectura sin bloqueo de una variante de la tienda (SEG-004: otra tienda no la ve). */
    @Query(value = """
            SELECT i.* FROM "InventarioVariante" i
            JOIN "VariantesProducto" v ON v."Id" = i."VarianteId"
            JOIN "Productos" p ON p."Id" = v."ProductoId"
            WHERE i."VarianteId" = :varianteId AND p."TiendaId" = :tiendaId
              AND i."Eliminado" = FALSE AND v."Eliminado" = FALSE AND p."Eliminado" = FALSE
            """, nativeQuery = true)
    Optional<InventarioVarianteEntidad> buscarDeTienda(@Param("tiendaId") UUID tiendaId,
                                                      @Param("varianteId") UUID varianteId);
}
