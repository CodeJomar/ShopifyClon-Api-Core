package shopify.api.core.modulos.orden.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;

public interface OrdenRepositorio extends JpaRepository<OrdenEntidad, UUID> {

    /** Idempotencia: una clave es, como mucho, una orden por tienda (UxOrdenesClaveIdempotencia). */
    Optional<OrdenEntidad> findFirstByTiendaIdAndClaveIdempotenciaAndEliminadoFalse(UUID tiendaId, UUID claveIdempotencia);
}
