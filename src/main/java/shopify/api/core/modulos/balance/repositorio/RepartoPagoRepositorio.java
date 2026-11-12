package shopify.api.core.modulos.balance.repositorio;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.balance.entidad.RepartoPagoEntidad;

public interface RepartoPagoRepositorio extends JpaRepository<RepartoPagoEntidad, UUID> {

    boolean existsByOrdenIdAndTipoAndEliminadoFalse(UUID ordenId, String tipo);

    Page<RepartoPagoEntidad> findByTiendaIdAndEliminadoFalseOrderByFechaCreacionDesc(UUID tiendaId, Pageable pagina);

    Page<RepartoPagoEntidad> findByEliminadoFalseOrderByFechaCreacionDesc(Pageable pagina);

    Page<RepartoPagoEntidad> findByPedidoIdAndEliminadoFalseOrderByFechaCreacionDesc(UUID pedidoId, Pageable pagina);
}
