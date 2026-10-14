package shopify.api.core.modulos.suscripcion.repositorio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;

public interface SuscripcionTiendaRepositorio extends JpaRepository<SuscripcionTiendaEntidad, UUID> {

    /** La única suscripción no cancelada de la tienda (SUS-002). */
    @Query("""
            SELECT s FROM SuscripcionTiendaEntidad s
            WHERE s.tiendaId = :tiendaId
              AND s.estado <> shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion.CANCELADA
              AND s.eliminado = FALSE
            """)
    Optional<SuscripcionTiendaEntidad> buscarNoCanceladaPorTienda(@Param("tiendaId") UUID tiendaId);

    /**
     * Incluye tiendas y suscripciones eliminadas a propósito: una prueba por usuario aunque la tienda
     * ya no exista (SUS-016).
     */
    @Query(value = """
            SELECT EXISTS (
              SELECT 1 FROM "SuscripcionesTienda" s
              JOIN "Tiendas" t ON t."Id" = s."TiendaId"
              WHERE t."PropietarioId" = :propietarioId AND s."FechaFinPrueba" IS NOT NULL
            )
            """, nativeQuery = true)
    boolean propietarioYaUsoPrueba(@Param("propietarioId") UUID propietarioId);
}
