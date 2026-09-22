package shopify.api.core.modules.catalogo.infrastructure.persistencia.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad.ProductoCategoriaEntidad;

import java.util.UUID;

public interface ProductoCategoriaJpaRepositorio extends JpaRepository<ProductoCategoriaEntidad, UUID> {
    void deleteByProductoId(UUID productoId);
}