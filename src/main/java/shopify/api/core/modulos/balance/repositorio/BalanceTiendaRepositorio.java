package shopify.api.core.modulos.balance.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.balance.entidad.BalanceTiendaEntidad;

public interface BalanceTiendaRepositorio extends JpaRepository<BalanceTiendaEntidad, UUID> {

    boolean existsByTiendaId(UUID tiendaId);

    Optional<BalanceTiendaEntidad> findByTiendaId(UUID tiendaId);
}
