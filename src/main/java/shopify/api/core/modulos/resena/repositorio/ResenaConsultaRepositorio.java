package shopify.api.core.modulos.resena.repositorio;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consultas de solo lectura de las reseñas (R-011). Una reseña oculta o eliminada nunca cuenta. */
@Repository
@RequiredArgsConstructor
public class ResenaConsultaRepositorio {

    private static final String VISIBLES = " r.\"Eliminado\" = FALSE AND r.\"Oculta\" = FALSE";

    private final NamedParameterJdbcTemplate jdbc;

    /** Producto publicado de la tienda por su identificador de URL. */
    public Optional<UUID> productoPublicado(UUID tiendaId, String slug) {
        return jdbc.queryForList("""
                SELECT p."Id" FROM "Productos" p
                WHERE p."TiendaId" = :tienda AND p."IdentificadorUrl" = :slug AND p."EstaPublicado" = TRUE
                  AND p."Eliminado" = FALSE
                """, new MapSqlParameterSource("tienda", tiendaId).addValue("slug", slug), UUID.class).stream().findFirst();
    }

    /** Compra verificada: una orden suya con dinero cobrado que contiene ese producto (en cualquier variante). */
    public boolean compro(UUID usuarioId, UUID productoId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM "Ordenes" o
                    JOIN "ItemsOrden" i ON i."OrdenId" = o."Id" AND i."Eliminado" = FALSE
                    JOIN "VariantesProducto" v ON v."Id" = i."VarianteId"
                    WHERE o."UsuarioId" = :usuario AND v."ProductoId" = :producto AND o."Eliminado" = FALSE
                      AND o."EstadoPago" IN ('PAGADO', 'PARCIALMENTE_REEMBOLSADO'))
                """, new MapSqlParameterSource("usuario", usuarioId).addValue("producto", productoId), Boolean.class));
    }

    /** Propietario o personal de la tienda. */
    public boolean esMiembro(UUID usuarioId, UUID tiendaId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "Tiendas" t WHERE t."Id" = :tienda AND t."PropietarioId" = :usuario AND t."Eliminado" = FALSE)
                    OR EXISTS (SELECT 1 FROM "PersonalTienda" pt WHERE pt."TiendaId" = :tienda AND pt."UsuarioId" = :usuario
                               AND pt."Eliminado" = FALSE)
                """, new MapSqlParameterSource("usuario", usuarioId).addValue("tienda", tiendaId), Boolean.class));
    }

    /** Promedio, total y distribución (1 a 5) de las reseñas visibles de un producto. */
    public Map<String, Object> resumen(UUID productoId) {
        return jdbc.queryForMap("""
                SELECT count(*) AS total, COALESCE(round(avg(r."Calificacion")::numeric, 1), 0) AS promedio,
                       count(*) FILTER (WHERE r."Calificacion" = 1) AS c1, count(*) FILTER (WHERE r."Calificacion" = 2) AS c2,
                       count(*) FILTER (WHERE r."Calificacion" = 3) AS c3, count(*) FILTER (WHERE r."Calificacion" = 4) AS c4,
                       count(*) FILTER (WHERE r."Calificacion" = 5) AS c5
                FROM "ResenasProducto" r WHERE r."ProductoId" = :producto AND """ + VISIBLES,
                new MapSqlParameterSource("producto", productoId));
    }

    /** Promedio (1 decimal) y cantidad de varios productos a la vez; los productos sin reseñas no aparecen. */
    public Map<UUID, Map<String, Object>> resumenes(Collection<UUID> productoIds) {
        Map<UUID, Map<String, Object>> resultado = new HashMap<>();
        if (productoIds.isEmpty()) {
            return resultado;
        }
        for (Map<String, Object> fila : jdbc.queryForList("""
                SELECT r."ProductoId" AS producto, count(*) AS total, round(avg(r."Calificacion")::numeric, 1) AS promedio
                FROM "ResenasProducto" r WHERE r."ProductoId" IN (:productos) AND """ + VISIBLES
                + " GROUP BY r.\"ProductoId\"", new MapSqlParameterSource("productos", productoIds))) {
            resultado.put((UUID) fila.get("producto"), fila);
        }
        return resultado;
    }

    public List<Map<String, Object>> lista(UUID productoId, String ordenSql, int limite, long desplazamiento) {
        return jdbc.queryForList("""
                SELECT r."Id", r."Calificacion", r."Comentario", r."FechaCreacion", u."Nombres", u."Apellidos"
                FROM "ResenasProducto" r JOIN "Usuarios" u ON u."Id" = r."UsuarioId"
                WHERE r."ProductoId" = :producto AND """ + VISIBLES + " ORDER BY " + ordenSql
                + " LIMIT :limite OFFSET :desplazamiento",
                new MapSqlParameterSource("producto", productoId).addValue("limite", limite).addValue("desplazamiento", desplazamiento));
    }

    public Optional<Map<String, Object>> deUsuario(UUID productoId, UUID usuarioId) {
        return jdbc.queryForList("""
                SELECT r."Id", r."Calificacion", r."Comentario", r."FechaCreacion", u."Nombres", u."Apellidos"
                FROM "ResenasProducto" r JOIN "Usuarios" u ON u."Id" = r."UsuarioId"
                WHERE r."ProductoId" = :producto AND r."UsuarioId" = :usuario AND """ + VISIBLES,
                new MapSqlParameterSource("producto", productoId).addValue("usuario", usuarioId)).stream().findFirst();
    }
}
