package shopify.api.core.modulos.catalogo.repositorio;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.catalogo.entidad.VarianteProductoEntidad;
import shopify.api.core.modulos.catalogo.modelo.VarianteVentaVista;
import java.util.List;

/**
 * Variantes de la tienda para venta. Devuelve también las eliminadas o de productos no publicados, marcadas como
 * no vendibles, para poder avisar al comprador (CAR-006).
 */
public interface VarianteVentaRepositorio extends JpaRepository<VarianteProductoEntidad, UUID> {

    @Query(value = """
            SELECT v."Id" AS "varianteId", p."Id" AS "productoId", p."Titulo" AS "tituloProducto",
                   v."Titulo" AS "tituloVariante", v."SKU" AS "sku", v."Precio" AS "precio",
                   v."RequiereEnvio" AS "requiereEnvio",
                   (SELECT i."UrlImagen" FROM "ImagenesProducto" i
                     WHERE i."ProductoId" = p."Id" AND i."EsPrincipal" = TRUE AND i."Eliminado" = FALSE
                     LIMIT 1) AS "urlImagen",
                   (v."Eliminado" = FALSE AND p."Eliminado" = FALSE AND p."EstaPublicado" = TRUE) AS "vendible"
            FROM "VariantesProducto" v
            JOIN "Productos" p ON p."Id" = v."ProductoId"
            WHERE p."TiendaId" = :tiendaId AND v."Id" IN (:varianteIds)
            """, nativeQuery = true)
    List<VarianteVentaVista> buscarParaVenta(@Param("tiendaId") UUID tiendaId,
                                             @Param("varianteIds") Collection<UUID> varianteIds);
}
