package shopify.api.core.modulos.suscripcion.repositorio;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.suscripcion.entidad.CobroSuscripcionEntidad;
import shopify.api.core.modulos.suscripcion.modelo.EstadoCobro;

public interface CobroSuscripcionRepositorio extends JpaRepository<CobroSuscripcionEntidad, UUID> {

    boolean existsBySuscripcionIdAndEstado(UUID suscripcionId, EstadoCobro estado);

    /**
     * Cobros PENDIENTE creados antes de {@code limite}: la pasarela nunca respondió. Se rechazan con su motivo
     * (T-007, cobros huérfanos).
     */
    @Modifying
    @Query(value = "UPDATE \"CobrosSuscripcion\" SET \"Estado\" = 'RECHAZADO', \"DetallesRespuesta\" = CAST(:detalle AS jsonb) "
            + "WHERE \"Estado\" = 'PENDIENTE' AND \"Eliminado\" = FALSE AND \"FechaCreacion\" < :limite", nativeQuery = true)
    int rechazarPendientesAnteriores(@Param("limite") Instant limite, @Param("detalle") String detalle);
}
