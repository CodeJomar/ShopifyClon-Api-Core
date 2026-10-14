package shopify.api.core.modulos.usuario.repositorio;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.usuario.entidad.TokenSeguridadEntidad;
import shopify.api.core.modulos.usuario.modelo.TipoCodigoSeguridad;

public interface TokenSeguridadRepositorio extends JpaRepository<TokenSeguridadEntidad, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE TokenSeguridadEntidad t SET t.usado = TRUE
            WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usado = FALSE
            """)
    int invalidarPendientes(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoCodigoSeguridad tipo);

    /** Pendientes no vencidos, el más reciente primero, bloqueados para escritura. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM TokenSeguridadEntidad t
            WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo AND t.usado = FALSE
              AND t.eliminado = FALSE AND t.fechaExpiracion > :ahora
            ORDER BY t.fechaCreacion DESC
            """)
    List<TokenSeguridadEntidad> bloquearPendientes(@Param("usuarioId") UUID usuarioId,
                                                   @Param("tipo") TipoCodigoSeguridad tipo,
                                                   @Param("ahora") Instant ahora,
                                                   Pageable limite);
}
