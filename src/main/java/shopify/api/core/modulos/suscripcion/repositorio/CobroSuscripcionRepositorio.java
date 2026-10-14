package shopify.api.core.modulos.suscripcion.repositorio;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.suscripcion.entidad.CobroSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.modelo.EstadoCobro;

public interface CobroSuscripcionRepositorio extends JpaRepository<CobroSuscripcionEntidad, UUID> {

    boolean existsBySuscripcionIdAndEstado(UUID suscripcionId, EstadoCobro estado);
}
