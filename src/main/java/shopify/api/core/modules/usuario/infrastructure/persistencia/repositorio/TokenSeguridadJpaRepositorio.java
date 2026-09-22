package shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.TokenSeguridadEntidad;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TokenSeguridadJpaRepositorio extends JpaRepository<TokenSeguridadEntidad, UUID> {
    Optional<TokenSeguridadEntidad> findByCodigoTokenAndTipoAndUsadoFalseAndFechaExpiracionAfter(
            String codigoToken, String tipo, Instant ahora);
}