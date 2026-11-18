package shopify.api.core.modulos.mercado.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import shopify.api.core.modulos.mercado.modelo.CategoriaMercado;
import shopify.api.core.modulos.mercado.modelo.FiltroProductosMercado;
import shopify.api.core.modulos.mercado.modelo.ProductoMercado;
import shopify.api.core.modulos.mercado.modelo.TiendaMercado;

/**
 * Consultas de solo lectura del mercado público (R-001). Cruzan tiendas, suscripciones y catálogo con SQL propio: es una
 * vista de lectura que no toca las entidades de otros módulos. Una tienda es visible si no está eliminada, está activa,
 * eligió aparecer y su suscripción permite operar; un producto lo es si está publicado y tiene variante e imagen principal
 * (misma regla que CAT-007 para publicar).
 */
@Repository
@RequiredArgsConstructor
public class MercadoRepositorio {

    private static final String TIENDA_VISIBLE = """
            t."Eliminado" = FALSE AND t."EstaActiva" = TRUE AND t."VisibleEnMercado" = TRUE
            AND EXISTS (SELECT 1 FROM "SuscripcionesTienda" s
                        WHERE s."TiendaId" = t."Id" AND s."Eliminado" = FALSE AND s."Estado" IN ('PRUEBA', 'ACTIVA'))
            """;

    private static final String PRODUCTO_VISIBLE = """
            p."EstaPublicado" = TRUE AND p."Eliminado" = FALSE
            AND EXISTS (SELECT 1 FROM "VariantesProducto" v WHERE v."ProductoId" = p."Id" AND v."Eliminado" = FALSE)
            AND EXISTS (SELECT 1 FROM "ImagenesProducto" i
                        WHERE i."ProductoId" = p."Id" AND i."EsPrincipal" = TRUE AND i."Eliminado" = FALSE)
            """;

    /** Comparación sin distinguir mayúsculas ni tildes (el texto del cliente llega ya normalizado igual). */
    private static String normalizado(String columna) {
        return "translate(lower(" + columna + "), 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunaeiouun')";
    }

    private static final RowMapper<ProductoMercado> FILA_PRODUCTO = (rs, fila) -> new ProductoMercado(
            rs.getObject("id", UUID.class),
            rs.getString("titulo"),
            rs.getString("identificador_url"),
            rs.getBigDecimal("precio_desde"),
            rs.getBigDecimal("precio_comparacion_desde"),
            rs.getString("moneda"),
            rs.getString("imagen_url"),
            rs.getBoolean("es_digital"),
            rs.getBoolean("hay_stock"),
            rs.getString("tienda_nombre"),
            rs.getString("tienda_subdominio"),
            rs.getString("tienda_logo_url"),
            rs.getBigDecimal("calificacion_promedio"),
            rs.getLong("cantidad_resenas"));

    private static final RowMapper<TiendaMercado> FILA_TIENDA = (rs, fila) -> new TiendaMercado(
            rs.getString("nombre"),
            rs.getString("subdominio"),
            rs.getString("descripcion"),
            rs.getString("logo_url"),
            rs.getString("moneda"),
            rs.getLong("cantidad"));

    private static final RowMapper<CategoriaMercado> FILA_CATEGORIA = (rs, fila) -> new CategoriaMercado(
            rs.getString("nombre"), rs.getString("slug"), rs.getLong("cantidad"));

    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ productos

    private static final String DESDE_PRODUCTOS = """
            FROM "Productos" p
            JOIN "Tiendas" t ON t."Id" = p."TiendaId"
            JOIN LATERAL (SELECT v."Precio", v."PrecioComparacion" FROM "VariantesProducto" v
                          WHERE v."ProductoId" = p."Id" AND v."Eliminado" = FALSE
                          ORDER BY v."Precio" ASC, v."Id" LIMIT 1) vmin ON TRUE
            JOIN LATERAL (SELECT i."UrlImagen" FROM "ImagenesProducto" i
                          WHERE i."ProductoId" = p."Id" AND i."EsPrincipal" = TRUE AND i."Eliminado" = FALSE
                          ORDER BY i."Posicion", i."Id" LIMIT 1) img ON TRUE
            LEFT JOIN LATERAL (SELECT round(avg(r."Calificacion")::numeric, 1) AS promedio, count(*) AS cantidad
                               FROM "ResenasProducto" r
                               WHERE r."ProductoId" = p."Id" AND r."Eliminado" = FALSE AND r."Oculta" = FALSE) rating ON TRUE
            """;

    public List<ProductoMercado> productos(FiltroProductosMercado f) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String donde = condicionesProductos(f, parametros);
        parametros.addValue("limite", f.limite()).addValue("desplazamiento", f.desplazamiento());
        String sql = """
                SELECT p."Id" AS id, p."Titulo" AS titulo, p."IdentificadorUrl" AS identificador_url,
                       vmin."Precio" AS precio_desde, vmin."PrecioComparacion" AS precio_comparacion_desde,
                       t."Moneda" AS moneda, img."UrlImagen" AS imagen_url, p."EsDigital" AS es_digital,
                       (p."EsDigital" = TRUE OR EXISTS (
                            SELECT 1 FROM "VariantesProducto" v2
                            JOIN "InventarioVariante" inv ON inv."VarianteId" = v2."Id" AND inv."Eliminado" = FALSE
                            WHERE v2."ProductoId" = p."Id" AND v2."Eliminado" = FALSE
                              AND (inv."RastreaInventario" = FALSE OR inv."PermitirVentaSinStock" = TRUE
                                   OR inv."CantidadDisponible" - inv."CantidadReservada" > 0))) AS hay_stock,
                       t."Nombre" AS tienda_nombre, t."Subdominio" AS tienda_subdominio, t."UrlLogo" AS tienda_logo_url,
                       rating.promedio AS calificacion_promedio, rating.cantidad AS cantidad_resenas
                """ + DESDE_PRODUCTOS + " WHERE " + donde
                + " ORDER BY " + f.orden().sql() + " LIMIT :limite OFFSET :desplazamiento";
        return jdbc.query(sql, parametros, FILA_PRODUCTO);
    }

    public long contarProductos(FiltroProductosMercado f) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String donde = condicionesProductos(f, parametros);
        Long total = jdbc.queryForObject("SELECT count(*) " + DESDE_PRODUCTOS + " WHERE " + donde, parametros, Long.class);
        return total == null ? 0 : total;
    }

    private static String condicionesProductos(FiltroProductosMercado f, MapSqlParameterSource parametros) {
        StringBuilder donde = new StringBuilder(PRODUCTO_VISIBLE).append(" AND ").append(TIENDA_VISIBLE);
        if (f.texto() != null) {
            donde.append(" AND (").append(normalizado("p.\"Titulo\"")).append(" LIKE :texto ESCAPE '\\' OR ")
                    .append(normalizado("t.\"Nombre\"")).append(" LIKE :texto ESCAPE '\\')");
            parametros.addValue("texto", "%" + f.texto() + "%");
        }
        if (f.categoria() != null) {
            donde.append("""
                     AND EXISTS (SELECT 1 FROM "ProductosCategorias" pc
                                 JOIN "Categorias" c ON c."Id" = pc."CategoriaId" AND c."Eliminado" = FALSE
                                 WHERE pc."ProductoId" = p."Id" AND pc."Eliminado" = FALSE
                                   AND c."IdentificadorUrl" = :categoria)
                    """);
            parametros.addValue("categoria", f.categoria());
        }
        if (f.tienda() != null) {
            donde.append(" AND t.\"Subdominio\" = :tienda");
            parametros.addValue("tienda", f.tienda());
        }
        if (f.precioMin() != null) {
            donde.append(" AND vmin.\"Precio\" >= :precioMin");
            parametros.addValue("precioMin", f.precioMin());
        }
        if (f.precioMax() != null) {
            donde.append(" AND vmin.\"Precio\" <= :precioMax");
            parametros.addValue("precioMax", f.precioMax());
        }
        return donde.toString();
    }

    public long contarProductosVisibles() {
        Long total = jdbc.queryForObject("SELECT count(*) " + DESDE_PRODUCTOS + " WHERE " + PRODUCTO_VISIBLE
                + " AND " + TIENDA_VISIBLE, new MapSqlParameterSource(), Long.class);
        return total == null ? 0 : total;
    }

    // ------------------------------------------------------------------ tiendas

    private static final String DESDE_TIENDAS = """
            FROM "Tiendas" t
            JOIN LATERAL (SELECT count(*) AS cantidad FROM "Productos" p
                          WHERE p."TiendaId" = t."Id" AND
            """ + PRODUCTO_VISIBLE + """
                          ) c ON c.cantidad > 0
            """;

    private static final String COLUMNAS_TIENDA = """
            SELECT t."Nombre" AS nombre, t."Subdominio" AS subdominio, t."Descripcion" AS descripcion,
                   t."UrlLogo" AS logo_url, t."Moneda" AS moneda, c.cantidad AS cantidad
            """;

    /** Tiendas visibles con al menos un producto visible; {@code texto} ya normalizado y escapado (o nulo). */
    public List<TiendaMercado> tiendas(String texto, boolean masRecientesPrimero, int limite, int desplazamiento) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("limite", limite).addValue("desplazamiento", desplazamiento);
        String donde = condicionesTiendas(texto, parametros);
        String orden = masRecientesPrimero ? "t.\"FechaCreacion\" DESC, t.\"Id\"" : "c.cantidad DESC, t.\"Nombre\", t.\"Id\"";
        return jdbc.query(COLUMNAS_TIENDA + DESDE_TIENDAS + " WHERE " + donde
                + " ORDER BY " + orden + " LIMIT :limite OFFSET :desplazamiento", parametros, FILA_TIENDA);
    }

    public long contarTiendas(String texto) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String donde = condicionesTiendas(texto, parametros);
        Long total = jdbc.queryForObject("SELECT count(*) " + DESDE_TIENDAS + " WHERE " + donde, parametros, Long.class);
        return total == null ? 0 : total;
    }

    public Optional<TiendaMercado> tienda(String subdominio) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("subdominio", subdominio);
        return jdbc.query(COLUMNAS_TIENDA + DESDE_TIENDAS + " WHERE " + TIENDA_VISIBLE + " AND t.\"Subdominio\" = :subdominio",
                parametros, FILA_TIENDA).stream().findFirst();
    }

    private static String condicionesTiendas(String texto, MapSqlParameterSource parametros) {
        StringBuilder donde = new StringBuilder(TIENDA_VISIBLE);
        if (texto != null) {
            donde.append(" AND ").append(normalizado("t.\"Nombre\"")).append(" LIKE :texto ESCAPE '\\'");
            parametros.addValue("texto", "%" + texto + "%");
        }
        return donde.toString();
    }

    // ------------------------------------------------------------------ categorías

    /** Categorías con productos visibles, agrupadas por identificador entre tiendas; {@code tienda} limita a una. */
    public List<CategoriaMercado> categorias(String tienda) {
        MapSqlParameterSource parametros = new MapSqlParameterSource();
        String filtroTienda = "";
        if (tienda != null) {
            filtroTienda = " AND t.\"Subdominio\" = :tienda";
            parametros.addValue("tienda", tienda);
        }
        String sql = """
                SELECT c."IdentificadorUrl" AS slug, MIN(c."Nombre") AS nombre, count(DISTINCT p."Id") AS cantidad
                FROM "Categorias" c
                JOIN "ProductosCategorias" pc ON pc."CategoriaId" = c."Id" AND pc."Eliminado" = FALSE
                JOIN "Productos" p ON p."Id" = pc."ProductoId"
                JOIN "Tiendas" t ON t."Id" = p."TiendaId"
                WHERE c."Eliminado" = FALSE AND
                """ + PRODUCTO_VISIBLE + " AND " + TIENDA_VISIBLE + filtroTienda
                + " GROUP BY c.\"IdentificadorUrl\" ORDER BY cantidad DESC, nombre";
        return jdbc.query(sql, parametros, FILA_CATEGORIA);
    }
}
