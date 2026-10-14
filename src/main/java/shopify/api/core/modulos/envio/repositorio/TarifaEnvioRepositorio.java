package shopify.api.core.modulos.envio.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.envio.entidad.TarifaEnvioEntidad;

public interface TarifaEnvioRepositorio extends JpaRepository<TarifaEnvioEntidad, UUID> {

    List<TarifaEnvioEntidad> findByTiendaIdAndEliminadoFalseOrderByOrdenAscNombreAsc(UUID tiendaId);

    List<TarifaEnvioEntidad> findByTiendaIdAndEstaActivaTrueAndEliminadoFalseOrderByOrdenAscNombreAsc(UUID tiendaId);

    Optional<TarifaEnvioEntidad> findByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    Optional<TarifaEnvioEntidad> findByIdAndTiendaIdAndEstaActivaTrueAndEliminadoFalse(UUID id, UUID tiendaId);

    boolean existsByTiendaIdAndEstaActivaTrueAndEliminadoFalse(UUID tiendaId);
}
