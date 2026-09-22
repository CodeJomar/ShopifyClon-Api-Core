package shopify.api.core.modules.usuario.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.usuario.infrastructure.persistencia.entidad.UsuarioEntidad;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioJpaRepositorio extends JpaRepository<UsuarioEntidad, UUID> {
    Optional<UsuarioEntidad> findByCorreoElectronicoAndEliminadoFalse(String correoElectronico);
    boolean existsByCorreoElectronicoAndEliminadoFalse(String correoElectronico);
}