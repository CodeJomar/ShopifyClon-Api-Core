package shopify.api.core.modulos.envio.repositorio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.envio.entidad.EventoEnvioEntidad;

public interface EventoEnvioRepositorio extends JpaRepository<EventoEnvioEntidad, UUID> {

    /** Más reciente primero; a igual instante, el último insertado (estable por Id no es posible, se confía en la fecha). */
    List<EventoEnvioEntidad> findByEnvioIdAndEliminadoFalseOrderByFechaEventoDesc(UUID envioId);
}
