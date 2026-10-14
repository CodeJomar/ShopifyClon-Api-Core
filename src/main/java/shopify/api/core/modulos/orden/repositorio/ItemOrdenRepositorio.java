package shopify.api.core.modulos.orden.repositorio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.orden.entidad.ItemOrdenEntidad;

public interface ItemOrdenRepositorio extends JpaRepository<ItemOrdenEntidad, UUID> {

    List<ItemOrdenEntidad> findByOrdenIdAndEliminadoFalseOrderByFechaCreacionAsc(UUID ordenId);
}
