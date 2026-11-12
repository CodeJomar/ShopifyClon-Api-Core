package shopify.api.core.modulos.propietario.repositorio;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Alertas de solo lectura del panel general. No escribe nada. */
@Repository
@RequiredArgsConstructor
public class AlertasPropietarioRepositorio {

    /** Una variante con esta cantidad vendible o menos (y rastreada, sin venta sin stock) cuenta como stock bajo. */
    public static final int UMBRAL_STOCK_BAJO = 5;

    private final NamedParameterJdbcTemplate jdbc;

    /** Variantes publicadas con stock bajo, por tienda; las tiendas sin alertas no aparecen. */
    public Map<UUID, Long> stockBajoPorTienda(Collection<UUID> tiendaIds) {
        Map<UUID, Long> resultado = new HashMap<>();
        if (tiendaIds.isEmpty()) {
            return resultado;
        }
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT p."TiendaId" AS tienda, COUNT(*) AS cuantas
                FROM "VariantesProducto" v
                JOIN "Productos" p ON p."Id" = v."ProductoId"
                JOIN "InventarioVariante" i ON i."VarianteId" = v."Id"
                WHERE p."TiendaId" IN (:tiendas) AND p."Eliminado" = FALSE AND p."EstaPublicado" = TRUE
                  AND v."Eliminado" = FALSE AND i."RastreaInventario" = TRUE AND i."PermitirVentaSinStock" = FALSE
                  AND (i."CantidadDisponible" - i."CantidadReservada") <= :umbral
                GROUP BY p."TiendaId"
                """, new MapSqlParameterSource().addValue("tiendas", tiendaIds).addValue("umbral", UMBRAL_STOCK_BAJO));
        for (Map<String, Object> fila : filas) {
            resultado.put((UUID) fila.get("tienda"), ((Number) fila.get("cuantas")).longValue());
        }
        return resultado;
    }
}
