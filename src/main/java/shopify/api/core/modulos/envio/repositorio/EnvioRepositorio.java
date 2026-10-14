package shopify.api.core.modulos.envio.repositorio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.envio.entidad.EnvioEntidad;

public interface EnvioRepositorio extends JpaRepository<EnvioEntidad, UUID> {

    Optional<EnvioEntidad> findFirstByOrdenIdAndEliminadoFalse(UUID ordenId);

    Optional<EnvioEntidad> findFirstByIdAndEliminadoFalse(UUID id);

    /** Bloqueo del envío para una transición (FOR UPDATE; se libera al commit). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EnvioEntidad e WHERE e.id = :id AND e.eliminado = FALSE")
    Optional<EnvioEntidad> bloquearPorId(@Param("id") UUID id);
}
