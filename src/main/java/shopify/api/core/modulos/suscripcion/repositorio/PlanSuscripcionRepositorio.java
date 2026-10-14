package shopify.api.core.modulos.suscripcion.repositorio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import shopify.api.core.modulos.suscripcion.entidad.PlanSuscripcionEntidad;

public interface PlanSuscripcionRepositorio extends JpaRepository<PlanSuscripcionEntidad, UUID> {

    List<PlanSuscripcionEntidad> findByEsPublicoTrueAndEliminadoFalseOrderByOrdenAsc();

    Optional<PlanSuscripcionEntidad> findByCodigoAndEliminadoFalse(String codigo);
}
