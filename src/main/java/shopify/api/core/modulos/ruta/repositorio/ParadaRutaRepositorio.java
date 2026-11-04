package shopify.api.core.modulos.ruta.repositorio;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.ruta.entidad.ParadaRutaEntidad;

public interface ParadaRutaRepositorio extends JpaRepository<ParadaRutaEntidad, UUID> {

    List<ParadaRutaEntidad> findByRutaIdAndEliminadoFalseOrderByPosicion(UUID rutaId);
}
