package shopify.api.core.modulos.suscripcion.repositorio;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.suscripcion.entidad.CobroSuscripcionEntidad;

public interface CobroSuscripcionRepositorio extends JpaRepository<CobroSuscripcionEntidad, UUID> {
}
