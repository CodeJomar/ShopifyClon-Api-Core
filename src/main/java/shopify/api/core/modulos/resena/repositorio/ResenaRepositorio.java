package shopify.api.core.modulos.resena.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.resena.entidad.ResenaEntidad;

public interface ResenaRepositorio extends JpaRepository<ResenaEntidad, UUID> {

    Optional<ResenaEntidad> findFirstByProductoIdAndUsuarioIdAndEliminadoFalse(UUID productoId, UUID usuarioId);

    Optional<ResenaEntidad> findFirstByIdAndEliminadoFalse(UUID id);
}
