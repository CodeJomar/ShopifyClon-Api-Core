package shopify.api.core.modulos.catalogo.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.ImagenProductoEntidad;

public interface ImagenProductoRepositorio extends JpaRepository<ImagenProductoEntidad, UUID> {

    List<ImagenProductoEntidad> findByProductoIdAndEliminadoFalseOrderByPosicionAscFechaCreacionAsc(UUID productoId);

    /** Imágenes principales de varios productos, en una sola consulta (listado del panel y vitrina). */
    List<ImagenProductoEntidad> findByProductoIdInAndEliminadoFalseAndEsPrincipalTrue(Collection<UUID> productoIds);

    /** Imagen por id solo si su producto es de la tienda (SEG-004). */
    @Query("""
            SELECT i FROM ImagenProductoEntidad i, ProductoEntidad p
            WHERE i.id = :id AND i.productoId = p.id AND p.tiendaId = :tiendaId
              AND i.eliminado = FALSE AND p.eliminado = FALSE
            """)
    Optional<ImagenProductoEntidad> buscarDeTienda(@Param("id") UUID id, @Param("tiendaId") UUID tiendaId);

    /** CAT-010: desmarca la principal antes de marcar otra (UxImagenPrincipalProducto). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ImagenProductoEntidad i SET i.esPrincipal = FALSE
            WHERE i.productoId = :productoId AND i.esPrincipal = TRUE AND i.eliminado = FALSE
            """)
    int desmarcarPrincipales(@Param("productoId") UUID productoId);
}
