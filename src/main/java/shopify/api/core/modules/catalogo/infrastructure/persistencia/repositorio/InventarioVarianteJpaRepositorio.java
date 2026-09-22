package shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.InventarioVarianteEntidad;

import java.util.Optional;
import java.util.UUID;

public interface InventarioVarianteJpaRepositorio extends JpaRepository<InventarioVarianteEntidad, UUID> {
    Optional<InventarioVarianteEntidad> findByVarianteIdAndEliminadoFalse(UUID varianteId);
}