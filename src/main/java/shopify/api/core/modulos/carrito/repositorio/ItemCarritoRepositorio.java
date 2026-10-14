package shopify.api.core.modulos.carrito.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.carrito.entidad.ItemCarritoEntidad;

public interface ItemCarritoRepositorio extends JpaRepository<ItemCarritoEntidad, UUID> {

    List<ItemCarritoEntidad> findByCarritoIdAndEliminadoFalseOrderByFechaCreacionAsc(UUID carritoId);

    Optional<ItemCarritoEntidad> findFirstByCarritoIdAndVarianteIdAndEliminadoFalse(UUID carritoId, UUID varianteId);
}
