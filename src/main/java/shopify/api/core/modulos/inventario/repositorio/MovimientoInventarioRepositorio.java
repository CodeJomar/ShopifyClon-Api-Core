package shopify.api.core.modulos.inventario.repositorio;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.inventario.entidad.MovimientoInventarioEntidad;

public interface MovimientoInventarioRepositorio extends JpaRepository<MovimientoInventarioEntidad, UUID> {
}
