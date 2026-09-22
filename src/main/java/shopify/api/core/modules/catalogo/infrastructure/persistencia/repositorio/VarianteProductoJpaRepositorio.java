package shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.VarianteProductoEntidad;

import java.util.Optional;
import java.util.UUID;

public interface VarianteProductoJpaRepositorio extends JpaRepository<VarianteProductoEntidad, UUID> {
    boolean existsBySkuAndEliminadoFalse(String sku);
    Optional<VarianteProductoEntidad> findByIdAndEliminadoFalse(UUID id);
}