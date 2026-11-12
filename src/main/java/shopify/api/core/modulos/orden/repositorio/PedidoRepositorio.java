package shopify.api.core.modulos.orden.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import shopify.api.core.modulos.orden.entidad.PedidoEntidad;

public interface PedidoRepositorio extends JpaRepository<PedidoEntidad, UUID> {

    Optional<PedidoEntidad> findFirstByTokenAccesoAndEliminadoFalse(String tokenAcceso);

    Optional<PedidoEntidad> findFirstByClaveIdempotenciaAndEliminadoFalse(UUID claveIdempotencia);

    Optional<PedidoEntidad> findFirstByIdAndUsuarioIdAndEliminadoFalse(UUID id, UUID usuarioId);

    Optional<PedidoEntidad> findFirstByIdAndEliminadoFalse(UUID id);

    Page<PedidoEntidad> findByUsuarioIdAndEliminadoFalseOrderByFechaCreacionDesc(UUID usuarioId, Pageable pagina);

    @Query(value = "SELECT nextval('\"SecuenciaNumeroPedido\"')", nativeQuery = true)
    long siguienteNumero();
}
