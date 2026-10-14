package shopify.api.core.modulos.pago.repositorio;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.orden.modelo.PasarelaPago;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;
import shopify.api.core.modulos.pago.modelo.EstadoRegistroPago;

public interface PagoRepositorio extends JpaRepository<PagoEntidad, UUID> {

    Optional<PagoEntidad> findByIdAndEliminadoFalse(UUID id);

    List<PagoEntidad> findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(UUID ordenId);

    /** Un webhook llega con el id del intento; el par (pasarela, transacción) es único (UxPagosPasarelaTransaccion). */
    Optional<PagoEntidad> findFirstByPasarelaPagoAndTransaccionIdAndEliminadoFalse(PasarelaPago pasarela, String transaccionId);

    /** Cargo en línea todavía pendiente de la orden, para reutilizar su intento al reintentar. */
    Optional<PagoEntidad> findFirstByOrdenIdAndPasarelaPagoAndEstadoAndEliminadoFalse(UUID ordenId, PasarelaPago pasarela,
                                                                                     EstadoRegistroPago estado);

    /** Bloqueo del pago para confirmar o rechazar una sola vez aunque el webhook llegue repetido (PAG-003). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PagoEntidad p WHERE p.id = :id AND p.eliminado = FALSE")
    Optional<PagoEntidad> bloquearPorId(@Param("id") UUID id);
}
