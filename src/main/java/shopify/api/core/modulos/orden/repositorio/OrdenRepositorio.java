package shopify.api.core.modulos.orden.repositorio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;

public interface OrdenRepositorio extends JpaRepository<OrdenEntidad, UUID> {

    /** Idempotencia: una clave es, como mucho, una orden por tienda (UxOrdenesClaveIdempotencia). */
    Optional<OrdenEntidad> findFirstByTiendaIdAndClaveIdempotenciaAndEliminadoFalse(UUID tiendaId, UUID claveIdempotencia);

    Optional<OrdenEntidad> findFirstByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    /** Bloqueo de la orden para una transición de estado (FOR UPDATE; se libera al commit). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenEntidad o WHERE o.id = :id AND o.tiendaId = :tiendaId AND o.eliminado = FALSE")
    Optional<OrdenEntidad> bloquearPorIdYTienda(@Param("id") UUID id, @Param("tiendaId") UUID tiendaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrdenEntidad o WHERE o.id = :id AND o.eliminado = FALSE")
    Optional<OrdenEntidad> bloquearPorId(@Param("id") UUID id);
}
