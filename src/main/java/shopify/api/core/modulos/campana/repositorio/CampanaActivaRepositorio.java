package shopify.api.core.modulos.campana.repositorio;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** La fila única que guarda la campaña vigente. */
@Repository
@RequiredArgsConstructor
public class CampanaActivaRepositorio {

    private final JdbcTemplate jdbc;

    /** Código de la campaña vigente, o vacío si no hay ninguna. */
    public Optional<String> codigoActivo() {
        return jdbc.queryForList("SELECT \"Codigo\" FROM \"CampanaActiva\" WHERE \"Id\" = 1", String.class).stream()
                .filter(codigo -> codigo != null && !codigo.isBlank()).findFirst();
    }

    public void guardar(String codigo, UUID usuarioId) {
        jdbc.update("UPDATE \"CampanaActiva\" SET \"Codigo\" = ?, \"FechaEdicion\" = CURRENT_TIMESTAMP, \"UsuarioEdicion\" = ? "
                + "WHERE \"Id\" = 1", codigo, usuarioId);
    }
}
