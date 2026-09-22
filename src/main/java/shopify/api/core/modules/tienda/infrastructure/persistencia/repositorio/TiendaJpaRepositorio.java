package shopify.api.core.modules.tienda.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.tienda.infrastructure.persistencia.entidad.TiendaEntidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TiendaJpaRepositorio extends JpaRepository<TiendaEntidad, UUID> {
    Optional<TiendaEntidad> findByIdAndEliminadoFalse(UUID id);
    Optional<TiendaEntidad> findBySubdominioIgnoreCaseAndEliminadoFalse(String subdominio);
    List<TiendaEntidad> findByPropietarioIdAndEliminadoFalse(UUID propietarioId);
    boolean existsBySubdominioIgnoreCaseAndEliminadoFalse(String subdominio);
}