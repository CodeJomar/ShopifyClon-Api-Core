package shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.RolEntidad;

import java.util.Optional;
import java.util.UUID;

public interface RolJpaRepositorio extends JpaRepository<RolEntidad, UUID> {
    Optional<RolEntidad> findByCodigoAndEliminadoFalse(String codigo);
}