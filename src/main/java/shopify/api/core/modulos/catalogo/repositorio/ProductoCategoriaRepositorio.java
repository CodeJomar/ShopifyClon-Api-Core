package shopify.api.core.modulos.catalogo.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.ProductoCategoriaEntidad;

public interface ProductoCategoriaRepositorio extends JpaRepository<ProductoCategoriaEntidad, UUID> {

    List<ProductoCategoriaEntidad> findByProductoIdInAndEliminadoFalse(Collection<UUID> productoIds);

    List<ProductoCategoriaEntidad> findByProductoIdAndEliminadoFalse(UUID productoId);

    /** Al eliminar una categoría, sus filas de ProductosCategorias se eliminan con ella. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductoCategoriaEntidad pc SET pc.eliminado = TRUE WHERE pc.categoriaId = :categoriaId AND pc.eliminado = FALSE")
    int quitarCategoria(@Param("categoriaId") UUID categoriaId);

    /** Reemplaza el conjunto de categorías de un producto (CAT-009). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ProductoCategoriaEntidad pc SET pc.eliminado = TRUE WHERE pc.productoId = :productoId AND pc.eliminado = FALSE")
    int quitarTodas(@Param("productoId") UUID productoId);
}
