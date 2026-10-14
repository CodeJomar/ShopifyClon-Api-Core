package shopify.api.core.modulos.usuario.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.usuario.entidad.DireccionUsuarioEntidad;

public interface DireccionUsuarioRepositorio extends JpaRepository<DireccionUsuarioEntidad, UUID> {

    List<DireccionUsuarioEntidad> findByUsuarioIdAndEliminadoFalseOrderByEsPrincipalDescFechaCreacionDesc(UUID usuarioId);

    Optional<DireccionUsuarioEntidad> findByIdAndUsuarioIdAndEliminadoFalse(UUID id, UUID usuarioId);

    Optional<DireccionUsuarioEntidad> findFirstByUsuarioIdAndEliminadoFalseOrderByFechaCreacionDesc(UUID usuarioId);

    long countByUsuarioIdAndEliminadoFalse(UUID usuarioId);

    /** Desmarca la principal antes de marcar otra, para no violar {@code UxDireccionPrincipalUsuario}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE DireccionUsuarioEntidad d SET d.esPrincipal = FALSE
            WHERE d.usuarioId = :usuarioId AND d.esPrincipal = TRUE AND d.eliminado = FALSE
            """)
    int desmarcarPrincipales(@Param("usuarioId") UUID usuarioId);
}
