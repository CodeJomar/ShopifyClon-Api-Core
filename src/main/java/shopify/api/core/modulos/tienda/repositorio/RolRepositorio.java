package shopify.api.core.modulos.tienda.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.tienda.entidad.RolEntidad;

public interface RolRepositorio extends JpaRepository<RolEntidad, UUID> {

    Optional<RolEntidad> findByCodigoAndEliminadoFalse(String codigo);
}
