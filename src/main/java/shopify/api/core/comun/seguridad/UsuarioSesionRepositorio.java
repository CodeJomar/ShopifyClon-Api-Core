package shopify.api.core.comun.seguridad;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consulta de solo lectura que permite a {@code comun} validar la sesión sin depender de módulos. */
@Repository
@RequiredArgsConstructor
public class UsuarioSesionRepositorio {

    /**
     * A-007: además de existir y estar activo, el token debe haberse emitido después del último cambio de clave o
     * cierre de sesiones ({@code SesionesValidasDesde}); si no, se trata como sesión inexistente (401 SEG-007).
     */
    private static final String SQL_ACTIVO = """
            SELECT "Id", "CorreoElectronico", "EsAdministradorPlataforma",
                   COALESCE((SELECT array_agg(rp."Permiso") FROM "RolesPlataformaPermisos" rp
                             JOIN "RolesPlataforma" r ON r."Id" = rp."RolId" AND r."Eliminado" = FALSE
                             WHERE rp."RolId" = "Usuarios"."RolPlataformaId"), ARRAY[]::varchar[]) AS "Permisos"
            FROM "Usuarios"
            WHERE "Id" = :id AND "EstaActivo" = TRUE AND "Eliminado" = FALSE
              AND ("SesionesValidasDesde" IS NULL OR "SesionesValidasDesde" <= :emitido)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<UsuarioAutenticado> buscarActivoPorId(UUID id, Instant tokenEmitido) {
        MapSqlParameterSource parametros = new MapSqlParameterSource("id", id)
                .addValue("emitido", Timestamp.from(tokenEmitido));
        return jdbc.query(SQL_ACTIVO, parametros, (rs, fila) -> new UsuarioAutenticado(
                rs.getObject("Id", UUID.class),
                rs.getString("CorreoElectronico"),
                rs.getBoolean("EsAdministradorPlataforma"),
                permisos((String[]) rs.getArray("Permisos").getArray()))).stream().findFirst();
    }

    /** Solo los nombres que existen en el catálogo; un permiso retirado del código se ignora. */
    private static Set<PermisoPlataforma> permisos(String[] nombres) {
        Set<PermisoPlataforma> resultado = EnumSet.noneOf(PermisoPlataforma.class);
        for (String nombre : nombres) {
            try {
                resultado.add(PermisoPlataforma.valueOf(nombre));
            } catch (IllegalArgumentException ignorado) {
                // permiso desconocido: no concede nada
            }
        }
        return resultado;
    }
}
