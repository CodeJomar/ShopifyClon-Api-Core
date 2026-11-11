package shopify.api.core.comun.seguridad;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
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
            SELECT "Id", "CorreoElectronico", "EsAdministradorPlataforma"
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
                rs.getBoolean("EsAdministradorPlataforma"))).stream().findFirst();
    }
}
