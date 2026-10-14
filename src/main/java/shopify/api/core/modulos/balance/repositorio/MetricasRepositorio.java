package shopify.api.core.modulos.balance.repositorio;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas nativas de solo lectura para las métricas (MET-001) y las comisiones de plataforma. No escriben nada.
 * Las fechas de los cargos se agrupan con {@code AT TIME ZONE} en la zona horaria de la tienda.
 */
@Repository
@RequiredArgsConstructor
public class MetricasRepositorio {

    private final JdbcTemplate jdbc;

    /** Moneda y zona horaria de la tienda (sin depender del servicio de tienda: evita un ciclo de módulos). */
    public Optional<Map<String, Object>> infoTienda(UUID tiendaId) {
        return jdbc.queryForList("SELECT \"Moneda\", \"ZonaHoraria\" FROM \"Tiendas\" WHERE \"Id\" = ? AND \"Eliminado\" = FALSE",
                tiendaId).stream().findFirst();
    }

    /** Cifras acumuladas del balance; vacío si la tienda aún no tiene balance. */
    public Optional<Map<String, Object>> balance(UUID tiendaId) {
        return jdbc.queryForList("SELECT \"TotalVentasHistorico\", \"TotalReembolsado\", \"TotalComisiones\", "
                + "\"SaldoPendienteCobro\", \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ? "
                + "AND \"Eliminado\" = FALSE", tiendaId).stream().findFirst();
    }

    /** Cargos aprobados por día (zona de la tienda): importe bruto y órdenes distintas. */
    public List<Map<String, Object>> cargosPorDia(String zona, UUID tiendaId, Instant inicio, Instant fin) {
        return jdbc.queryForList("SELECT CAST(p.\"FechaCreacion\" AT TIME ZONE CAST(? AS text) AS date) AS dia, "
                + "COALESCE(SUM(p.\"Monto\"), 0) AS importe, COUNT(DISTINCT p.\"OrdenId\") AS ordenes "
                + "FROM \"Pagos\" p JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" "
                + "WHERE o.\"TiendaId\" = ? AND p.\"Tipo\" = 'CARGO' AND p.\"Estado\" = 'APROBADO' "
                + "AND p.\"Eliminado\" = FALSE AND p.\"FechaCreacion\" >= ? AND p.\"FechaCreacion\" < ? GROUP BY 1",
                zona, tiendaId, Timestamp.from(inicio), Timestamp.from(fin));
    }

    /** Reembolsos aprobados por día (zona de la tienda). */
    public List<Map<String, Object>> reembolsosPorDia(String zona, UUID tiendaId, Instant inicio, Instant fin) {
        return jdbc.queryForList("SELECT CAST(p.\"FechaCreacion\" AT TIME ZONE CAST(? AS text) AS date) AS dia, "
                + "COALESCE(SUM(p.\"Monto\"), 0) AS importe "
                + "FROM \"Pagos\" p JOIN \"Ordenes\" o ON o.\"Id\" = p.\"OrdenId\" "
                + "WHERE o.\"TiendaId\" = ? AND p.\"Tipo\" = 'REEMBOLSO' AND p.\"Estado\" = 'APROBADO' "
                + "AND p.\"Eliminado\" = FALSE AND p.\"FechaCreacion\" >= ? AND p.\"FechaCreacion\" < ? GROUP BY 1",
                zona, tiendaId, Timestamp.from(inicio), Timestamp.from(fin));
    }

    /**
     * Productos de órdenes pagadas en el rango (con cargo aprobado) y no canceladas, por unidades vendidas.
     * Las unidades son brutas: los reembolsos no las restan (ver decisiones del reporte).
     */
    public List<Map<String, Object>> topProductos(UUID tiendaId, Instant inicio, Instant fin, int limite) {
        return jdbc.queryForList("SELECT i.\"VarianteId\", MAX(i.\"TituloProducto\") AS titulo, "
                + "MAX(i.\"TituloVariante\") AS variante, SUM(i.\"Cantidad\") AS unidades, SUM(i.\"TotalLinea\") AS importe "
                + "FROM \"ItemsOrden\" i JOIN \"Ordenes\" o ON o.\"Id\" = i.\"OrdenId\" "
                + "WHERE o.\"TiendaId\" = ? AND o.\"Estado\" <> 'CANCELADA' AND o.\"Eliminado\" = FALSE "
                + "AND i.\"Eliminado\" = FALSE "
                + "AND EXISTS (SELECT 1 FROM \"Pagos\" p WHERE p.\"OrdenId\" = o.\"Id\" AND p.\"Tipo\" = 'CARGO' "
                + "AND p.\"Estado\" = 'APROBADO' AND p.\"Eliminado\" = FALSE "
                + "AND p.\"FechaCreacion\" >= ? AND p.\"FechaCreacion\" < ?) "
                + "GROUP BY i.\"VarianteId\" ORDER BY unidades DESC, importe DESC LIMIT ?",
                tiendaId, Timestamp.from(inicio), Timestamp.from(fin), limite);
    }

    /** Órdenes abiertas, pagadas y sin preparar que requieren envío (estado actual, no del rango). */
    public long ordenesPorPreparar(UUID tiendaId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM \"Ordenes\" o WHERE o.\"TiendaId\" = ? AND o.\"Eliminado\" = FALSE "
                + "AND o.\"Estado\" = 'ABIERTA' AND o.\"EstadoPago\" = 'PAGADO' AND o.\"EstadoPreparacion\" = 'NO_PREPARADO' "
                + "AND EXISTS (SELECT 1 FROM \"ItemsOrden\" i WHERE i.\"OrdenId\" = o.\"Id\" AND i.\"RequiereEnvio\" = TRUE "
                + "AND i.\"Eliminado\" = FALSE)", Long.class, tiendaId);
        return total == null ? 0L : total;
    }

    /** Comisiones por tienda, de mayor a menor, paginado. Solo para el administrador de plataforma. */
    public List<Map<String, Object>> comisionesPorTienda(int tamano, long desplazamiento) {
        return jdbc.queryForList("SELECT t.\"Id\", t.\"Nombre\", t.\"Subdominio\", p.\"Codigo\" AS plan, "
                + "b.\"TotalComisiones\", b.\"TotalVentasHistorico\" "
                + "FROM \"BalancesTienda\" b JOIN \"Tiendas\" t ON t.\"Id\" = b.\"TiendaId\" "
                + "LEFT JOIN \"SuscripcionesTienda\" s ON s.\"TiendaId\" = t.\"Id\" AND s.\"Estado\" <> 'CANCELADA' "
                + "AND s.\"Eliminado\" = FALSE LEFT JOIN \"PlanesSuscripcion\" p ON p.\"Id\" = s.\"PlanId\" "
                + "WHERE b.\"Eliminado\" = FALSE AND t.\"Eliminado\" = FALSE "
                + "ORDER BY b.\"TotalComisiones\" DESC, t.\"Id\" LIMIT ? OFFSET ?", tamano, desplazamiento);
    }

    public long contarComisionesPorTienda() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM \"BalancesTienda\" b JOIN \"Tiendas\" t ON t.\"Id\" = b.\"TiendaId\" "
                + "WHERE b.\"Eliminado\" = FALSE AND t.\"Eliminado\" = FALSE", Long.class);
        return total == null ? 0L : total;
    }
}
