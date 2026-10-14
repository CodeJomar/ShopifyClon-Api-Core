package shopify.api.core.comun.seguridad;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Consulta de solo lectura de la membresía de un usuario en una tienda (transversal). */
@Repository
@RequiredArgsConstructor
public class MembresiaTiendaRepositorio {

    private static final String SQL_ROL = """
            SELECT CASE WHEN t."PropietarioId" = :usuarioId THEN 'PROPIETARIO' ELSE r."Codigo" END AS rol
            FROM "Tiendas" t
            LEFT JOIN "PersonalTienda" p ON p."TiendaId" = t."Id" AND p."UsuarioId" = :usuarioId AND p."Eliminado" = FALSE
            LEFT JOIN "Roles" r ON r."Id" = p."RolId"
            WHERE t."Id" = :tiendaId AND t."Eliminado" = FALSE
              AND (t."PropietarioId" = :usuarioId OR p."Id" IS NOT NULL)
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<String> buscarCodigoRol(UUID tiendaId, UUID usuarioId) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("tiendaId", tiendaId)
                .addValue("usuarioId", usuarioId);
        return jdbc.query(SQL_ROL, parametros, (rs, fila) -> rs.getString("rol")).stream().findFirst();
    }
}
