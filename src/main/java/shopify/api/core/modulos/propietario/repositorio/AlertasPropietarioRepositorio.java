package shopify.api.core.modulos.propietario.repositorio;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas del panel general del propietario, agrupadas para TODAS sus tiendas a la vez (una consulta por tema, no por
 * tienda): con la base lejos cada consulta cuesta ~120 ms, así que el número de consultas manda. Solo lectura.
 */
@Repository
@RequiredArgsConstructor
public class AlertasPropietarioRepositorio {

    /** Una variante con esta cantidad vendible o menos (y rastreada, sin venta sin stock) cuenta como stock bajo. */
    public static final int UMBRAL_STOCK_BAJO = 5;

    /** Rango de fechas de una tienda, ya convertido a instantes en su zona horaria. */
    public record RangoTienda(UUID tiendaId, String zona, Instant inicio, Instant fin) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** Tiendas de las que la persona es propietaria, con su suscripción vigente (si la hay) y su plan. */
    public List<Map<String, Object>> tiendasDelPropietario(UUID usuarioId, int limite) {
        return jdbc.queryForList("""
                SELECT t."Id", t."Nombre", t."Subdominio", t."Ruc", t."Moneda", t."ZonaHoraria", t."EstaActiva",
                       s."Estado" AS estado, p."Codigo" AS plan
                FROM "Tiendas" t
                LEFT JOIN "SuscripcionesTienda" s ON s."TiendaId" = t."Id" AND s."Estado" <> 'CANCELADA' AND s."Eliminado" = FALSE
                LEFT JOIN "PlanesSuscripcion" p ON p."Id" = s."PlanId"
                WHERE t."PropietarioId" = :usuario AND t."Eliminado" = FALSE
                ORDER BY t."Nombre" LIMIT :limite
                """, new MapSqlParameterSource("usuario", usuarioId).addValue("limite", limite));
    }

    /** Saldo por cobrar de cada tienda; las tiendas sin balance no aparecen. */
    public Map<UUID, BigDecimal> saldos(Collection<UUID> tiendaIds) {
        Map<UUID, BigDecimal> resultado = new HashMap<>();
        if (tiendaIds.isEmpty()) {
            return resultado;
        }
        for (Map<String, Object> fila : jdbc.queryForList("""
                SELECT b."TiendaId" AS tienda, b."SaldoPendienteCobro" AS saldo FROM "BalancesTienda" b
                WHERE b."TiendaId" IN (:tiendas) AND b."Eliminado" = FALSE
                """, new MapSqlParameterSource("tiendas", tiendaIds))) {
            resultado.put((UUID) fila.get("tienda"), (BigDecimal) fila.get("saldo"));
        }
        return resultado;
    }

    /** Cargos aprobados por tienda y día (en la zona de cada tienda): importe bruto y órdenes distintas. */
    public List<Map<String, Object>> cargosPorDia(List<RangoTienda> rangos) {
        return porDia("""
                SELECT r.tienda AS tienda, CAST(p."FechaCreacion" AT TIME ZONE r.zona AS date) AS dia,
                       COALESCE(SUM(p."Monto"), 0) AS importe, COUNT(DISTINCT p."OrdenId") AS ordenes
                """, "CARGO", rangos);
    }

    /** Reembolsos aprobados por tienda y día (en la zona de cada tienda). */
    public List<Map<String, Object>> reembolsosPorDia(List<RangoTienda> rangos) {
        return porDia("""
                SELECT r.tienda AS tienda, CAST(p."FechaCreacion" AT TIME ZONE r.zona AS date) AS dia,
                       COALESCE(SUM(p."Monto"), 0) AS importe, 0 AS ordenes
                """, "REEMBOLSO", rangos);
    }

    private List<Map<String, Object>> porDia(String seleccion, String tipo, List<RangoTienda> rangos) {
        if (rangos.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parametros = new MapSqlParameterSource("tipo", tipo);
        StringBuilder valores = new StringBuilder();
        for (int i = 0; i < rangos.size(); i++) {
            RangoTienda r = rangos.get(i);
            valores.append(i == 0 ? "" : ", ").append("(CAST(:t").append(i).append(" AS uuid), CAST(:z").append(i)
                    .append(" AS text), CAST(:i").append(i).append(" AS timestamptz), CAST(:f").append(i).append(" AS timestamptz))");
            parametros.addValue("t" + i, r.tiendaId()).addValue("z" + i, r.zona())
                    .addValue("i" + i, Timestamp.from(r.inicio())).addValue("f" + i, Timestamp.from(r.fin()));
        }
        return jdbc.queryForList(seleccion + " FROM (VALUES " + valores + ") AS r(tienda, zona, inicio, fin) "
                + "JOIN \"Ordenes\" o ON o.\"TiendaId\" = r.tienda "
                + "JOIN \"Pagos\" p ON p.\"OrdenId\" = o.\"Id\" AND p.\"Tipo\" = :tipo AND p.\"Estado\" = 'APROBADO' "
                + "AND p.\"Eliminado\" = FALSE AND p.\"FechaCreacion\" >= r.inicio AND p.\"FechaCreacion\" < r.fin "
                + "GROUP BY 1, 2", parametros);
    }

    /** Órdenes abiertas, pagadas y sin preparar que requieren envío, por tienda. */
    public Map<UUID, Long> ordenesPorPreparar(Collection<UUID> tiendaIds) {
        Map<UUID, Long> resultado = new HashMap<>();
        if (tiendaIds.isEmpty()) {
            return resultado;
        }
        for (Map<String, Object> fila : jdbc.queryForList("""
                SELECT o."TiendaId" AS tienda, COUNT(*) AS cuantas FROM "Ordenes" o
                WHERE o."TiendaId" IN (:tiendas) AND o."Eliminado" = FALSE AND o."Estado" = 'ABIERTA'
                  AND o."EstadoPago" = 'PAGADO' AND o."EstadoPreparacion" = 'NO_PREPARADO'
                  AND EXISTS (SELECT 1 FROM "ItemsOrden" i WHERE i."OrdenId" = o."Id" AND i."RequiereEnvio" = TRUE
                              AND i."Eliminado" = FALSE)
                GROUP BY o."TiendaId"
                """, new MapSqlParameterSource("tiendas", tiendaIds))) {
            resultado.put((UUID) fila.get("tienda"), ((Number) fila.get("cuantas")).longValue());
        }
        return resultado;
    }

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
