package shopify.api.core.modulos.inventario.repositorio;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.inventario.entidad.InventarioVarianteEntidad;

public interface InventarioVarianteRepositorio extends JpaRepository<InventarioVarianteEntidad, UUID> {

    Optional<InventarioVarianteEntidad> findFirstByVarianteIdAndEliminadoFalse(UUID varianteId);

    List<InventarioVarianteEntidad> findByVarianteIdInAndEliminadoFalse(Collection<UUID> varianteIds);
}
