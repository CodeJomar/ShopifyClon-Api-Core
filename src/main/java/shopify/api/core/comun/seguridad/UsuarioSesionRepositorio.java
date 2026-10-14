package shopify.api.core.comun.seguridad;

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

    private static final String SQL_ACTIVO = """
            SELECT "Id", "CorreoElectronico", "EsAdministradorPlataforma"
            FROM "Usuarios"
            WHERE "Id" = :id AND "EstaActivo" = TRUE AND "Eliminado" = FALSE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<UsuarioAutenticado> buscarActivoPorId(UUID id) {
        return jdbc.query(SQL_ACTIVO, new MapSqlParameterSource("id", id), (rs, fila) -> new UsuarioAutenticado(
                rs.getObject("Id", UUID.class),
                rs.getString("CorreoElectronico"),
                rs.getBoolean("EsAdministradorPlataforma"))).stream().findFirst();
    }
}
