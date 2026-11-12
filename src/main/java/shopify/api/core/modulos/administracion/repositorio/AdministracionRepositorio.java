package shopify.api.core.modulos.administracion.repositorio;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de solo lectura de la administración de plataforma (B8). Todas son paginadas y parametrizadas; el
 * {@code q} se compara en minúsculas con LIKE escapado por el servicio.
 */
@Repository
@RequiredArgsConstructor
public class AdministracionRepositorio {

    private static final String TIENDAS_BASE = """
            FROM "Tiendas" t
            JOIN "Usuarios" u ON u."Id" = t."PropietarioId"
            LEFT JOIN "SuscripcionesTienda" s ON s."TiendaId" = t."Id" AND s."Estado" <> 'CANCELADA' AND s."Eliminado" = FALSE
            LEFT JOIN "PlanesSuscripcion" p ON p."Id" = s."PlanId"
            LEFT JOIN "BalancesTienda" b ON b."TiendaId" = t."Id" AND b."Eliminado" = FALSE
            WHERE t."Eliminado" = FALSE
              AND (CAST(:q AS text) IS NULL OR lower(t."Nombre") LIKE :q ESCAPE '\\' OR lower(t."Subdominio") LIKE :q ESCAPE '\\'
                   OR lower(u."CorreoElectronico") LIKE :q ESCAPE '\\')
              AND (CAST(:activa AS boolean) IS NULL OR t."EstaActiva" = CAST(:activa AS boolean))
              AND (CAST(:plan AS text) IS NULL OR p."Codigo" = CAST(:plan AS text))
            """;

    private static final String USUARIOS_BASE = """
            FROM "Usuarios" u
            WHERE u."Eliminado" = FALSE
              AND (CAST(:q AS text) IS NULL OR lower(u."CorreoElectronico") LIKE :q ESCAPE '\\'
                   OR lower(u."Nombres" || ' ' || u."Apellidos") LIKE :q ESCAPE '\\')
              AND (CAST(:estado AS text) IS NULL
                   OR (:estado = 'ACTIVO' AND u."EstaActivo" = TRUE AND (u."BloqueadoHasta" IS NULL OR u."BloqueadoHasta" <= now()))
                   OR (:estado = 'INACTIVO' AND u."EstaActivo" = FALSE)
                   OR (:estado = 'BLOQUEADO' AND u."BloqueadoHasta" > now()))
            """;

    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ tiendas

    public List<Map<String, Object>> tiendas(String q, Boolean activa, String plan, int limite, long desplazamiento) {
        return jdbc.queryForList("""
                SELECT t."Id", t."Nombre", t."Subdominio", t."EstaActiva", t."Moneda", t."FechaCreacion",
                       u."Id" AS "DuenioId", u."CorreoElectronico" AS "DuenioCorreo",
                       u."Nombres" || ' ' || u."Apellidos" AS "DuenioNombre",
                       p."Codigo" AS "Plan", s."Estado" AS "EstadoSuscripcion",
                       COALESCE(b."TotalVentasHistorico", 0) AS "Ventas", COALESCE(b."CantidadOrdenes", 0) AS "Ordenes"
                """ + TIENDAS_BASE + " ORDER BY t.\"FechaCreacion\" DESC, t.\"Id\" LIMIT :limite OFFSET :desplazamiento",
                filtrosTienda(q, activa, plan).addValue("limite", limite).addValue("desplazamiento", desplazamiento));
    }

    public long contarTiendas(String q, Boolean activa, String plan) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + TIENDAS_BASE, filtrosTienda(q, activa, plan), Long.class);
        return total == null ? 0L : total;
    }

    public Optional<Map<String, Object>> tienda(UUID id) {
        return jdbc.queryForList("""
                SELECT t."Id", t."Nombre", t."Subdominio", t."EstaActiva", t."Moneda", t."FechaCreacion", t."CorreoContacto",
                       u."Id" AS "DuenioId", u."CorreoElectronico" AS "DuenioCorreo",
                       u."Nombres" || ' ' || u."Apellidos" AS "DuenioNombre",
                       p."Codigo" AS "Plan", s."Estado" AS "EstadoSuscripcion", s."FinPeriodoActual",
                       COALESCE(b."TotalVentasHistorico", 0) AS "Ventas", COALESCE(b."CantidadOrdenes", 0) AS "Ordenes",
                       COALESCE(b."TotalReembolsado", 0) AS "Reembolsado", COALESCE(b."TotalComisiones", 0) AS "Comisiones",
                       COALESCE(b."SaldoPendienteCobro", 0) AS "Saldo"
                FROM "Tiendas" t
                JOIN "Usuarios" u ON u."Id" = t."PropietarioId"
                LEFT JOIN "SuscripcionesTienda" s ON s."TiendaId" = t."Id" AND s."Estado" <> 'CANCELADA' AND s."Eliminado" = FALSE
                LEFT JOIN "PlanesSuscripcion" p ON p."Id" = s."PlanId"
                LEFT JOIN "BalancesTienda" b ON b."TiendaId" = t."Id" AND b."Eliminado" = FALSE
                WHERE t."Id" = :id AND t."Eliminado" = FALSE
                """, new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    public List<Map<String, Object>> equipo(UUID tiendaId) {
        return jdbc.queryForList("""
                SELECT u."Id", u."CorreoElectronico", u."Nombres" || ' ' || u."Apellidos" AS "Nombre", r."Codigo" AS "Rol"
                FROM "PersonalTienda" pt
                JOIN "Usuarios" u ON u."Id" = pt."UsuarioId"
                JOIN "Roles" r ON r."Id" = pt."RolId"
                WHERE pt."TiendaId" = :id AND pt."Eliminado" = FALSE
                ORDER BY u."CorreoElectronico"
                """, new MapSqlParameterSource("id", tiendaId));
    }

    // ------------------------------------------------------------------ usuarios

    public List<Map<String, Object>> usuarios(String q, String estado, int limite, long desplazamiento) {
        return jdbc.queryForList("""
                SELECT u."Id", u."CorreoElectronico", u."Nombres", u."Apellidos", u."Telefono", u."EstaActivo",
                       u."BloqueadoHasta", u."DosPasosActivo", u."EsAdministradorPlataforma", u."FechaCreacion",
                       (SELECT COUNT(*) FROM "Tiendas" t WHERE t."PropietarioId" = u."Id" AND t."Eliminado" = FALSE) AS "TiendasPropias"
                """ + USUARIOS_BASE + " ORDER BY u.\"FechaCreacion\" DESC, u.\"Id\" LIMIT :limite OFFSET :desplazamiento",
                filtrosUsuario(q, estado).addValue("limite", limite).addValue("desplazamiento", desplazamiento));
    }

    public long contarUsuarios(String q, String estado) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + USUARIOS_BASE, filtrosUsuario(q, estado), Long.class);
        return total == null ? 0L : total;
    }

    public Optional<Map<String, Object>> usuario(UUID id) {
        return jdbc.queryForList("""
                SELECT u."Id", u."CorreoElectronico", u."Nombres", u."Apellidos", u."Telefono", u."EstaActivo",
                       u."BloqueadoHasta", u."DosPasosActivo", u."EsAdministradorPlataforma", u."FechaCreacion",
                       (SELECT COUNT(*) FROM "Tiendas" t WHERE t."PropietarioId" = u."Id" AND t."Eliminado" = FALSE) AS "TiendasPropias"
                FROM "Usuarios" u WHERE u."Id" = :id AND u."Eliminado" = FALSE
                """, new MapSqlParameterSource("id", id)).stream().findFirst();
    }

    public List<Map<String, Object>> tiendasDeUsuario(UUID usuarioId) {
        return jdbc.queryForList("""
                SELECT t."Id", t."Nombre", t."Subdominio",
                       CASE WHEN t."PropietarioId" = :id THEN 'PROPIETARIO' ELSE r."Codigo" END AS "Rol"
                FROM "Tiendas" t
                LEFT JOIN "PersonalTienda" pt ON pt."TiendaId" = t."Id" AND pt."UsuarioId" = :id AND pt."Eliminado" = FALSE
                LEFT JOIN "Roles" r ON r."Id" = pt."RolId"
                WHERE t."Eliminado" = FALSE AND (t."PropietarioId" = :id OR pt."Id" IS NOT NULL)
                ORDER BY t."Nombre"
                """, new MapSqlParameterSource("id", usuarioId));
    }

    // ------------------------------------------------------------------ auditoría

    public List<Map<String, Object>> auditoria(MapSqlParameterSource filtros, int limite, long desplazamiento) {
        return jdbc.queryForList("""
                SELECT a."Id", a."TiendaId", a."UsuarioId", a."Accion", a."Entidad", a."EntidadId", a."Motivo",
                       CAST(a."Detalle" AS text) AS "Detalle", a."FechaCreacion"
                """ + AUDITORIA_BASE + " ORDER BY a.\"FechaCreacion\" DESC, a.\"Id\" LIMIT :limite OFFSET :desplazamiento",
                new MapSqlParameterSource(filtros.getValues()).addValue("limite", limite).addValue("desplazamiento", desplazamiento));
    }

    public long contarAuditoria(MapSqlParameterSource filtros) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + AUDITORIA_BASE, filtros, Long.class);
        return total == null ? 0L : total;
    }

    private static final String AUDITORIA_BASE = """
            FROM "RegistroAuditoria" a
            WHERE a."Eliminado" = FALSE
              AND (CAST(:usuarioId AS uuid) IS NULL OR a."UsuarioId" = CAST(:usuarioId AS uuid))
              AND (CAST(:entidad AS text) IS NULL OR a."Entidad" = CAST(:entidad AS text))
              AND (CAST(:accion AS text) IS NULL OR a."Accion" = CAST(:accion AS text))
              AND (CAST(:soloSeguridad AS boolean) IS NULL
                   OR (CAST(:soloSeguridad AS boolean) = TRUE AND a."Accion" LIKE 'SEGURIDAD\\_%')
                   OR (CAST(:soloSeguridad AS boolean) = FALSE AND a."Accion" NOT LIKE 'SEGURIDAD\\_%'))
              AND (CAST(:desde AS timestamptz) IS NULL OR a."FechaCreacion" >= CAST(:desde AS timestamptz))
              AND (CAST(:hasta AS timestamptz) IS NULL OR a."FechaCreacion" < CAST(:hasta AS timestamptz))
            """;

    // ------------------------------------------------------------------ pedidos

    public List<Map<String, Object>> pedidos(String q, int limite, long desplazamiento) {
        return jdbc.queryForList("""
                SELECT pe."Id", pe."NumeroPedido", pe."CorreoCliente", pe."Moneda", pe."Total", pe."PasarelaPago", pe."FechaCreacion",
                       (SELECT COUNT(*) FROM "Ordenes" o WHERE o."PedidoId" = pe."Id" AND o."Eliminado" = FALSE) AS "Ordenes",
                       (SELECT COUNT(*) FROM "Ordenes" o WHERE o."PedidoId" = pe."Id" AND o."Eliminado" = FALSE
                           AND o."EstadoPago" IN ('PAGADO','PARCIALMENTE_REEMBOLSADO','REEMBOLSADO')) AS "Pagadas"
                """ + PEDIDOS_BASE + " ORDER BY pe.\"FechaCreacion\" DESC, pe.\"Id\" LIMIT :limite OFFSET :desplazamiento",
                new MapSqlParameterSource("q", q).addValue("limite", limite).addValue("desplazamiento", desplazamiento));
    }

    public long contarPedidos(String q) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + PEDIDOS_BASE, new MapSqlParameterSource("q", q), Long.class);
        return total == null ? 0L : total;
    }

    private static final String PEDIDOS_BASE = """
            FROM "Pedidos" pe
            WHERE pe."Eliminado" = FALSE
              AND (CAST(:q AS text) IS NULL OR lower(pe."NumeroPedido") LIKE :q ESCAPE '\\' OR lower(pe."CorreoCliente") LIKE :q ESCAPE '\\')
            """;

    // ------------------------------------------------------------------ parámetros

    private static MapSqlParameterSource filtrosTienda(String q, Boolean activa, String plan) {
        return new MapSqlParameterSource().addValue("q", q).addValue("activa", activa).addValue("plan", plan);
    }

    private static MapSqlParameterSource filtrosUsuario(String q, String estado) {
        return new MapSqlParameterSource().addValue("q", q).addValue("estado", estado);
    }
}
