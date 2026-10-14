package shopify.api.core.modulos.pago.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.pago.entidad.PagoEntidad;

public interface PagoRepositorio extends JpaRepository<PagoEntidad, UUID> {

    Optional<PagoEntidad> findByIdAndEliminadoFalse(UUID id);

    List<PagoEntidad> findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(UUID ordenId);
}
