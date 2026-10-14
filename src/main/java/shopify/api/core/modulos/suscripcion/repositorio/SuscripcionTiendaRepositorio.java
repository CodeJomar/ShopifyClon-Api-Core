package shopify.api.core.modulos.suscripcion.repositorio;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.suscripcion.entidad.SuscripcionTiendaEntidad;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionPlataformaVista;

public interface SuscripcionTiendaRepositorio extends JpaRepository<SuscripcionTiendaEntidad, UUID> {

    /** La única suscripción no cancelada de la tienda (SUS-002). */
    @Query("""
            SELECT s FROM SuscripcionTiendaEntidad s
            WHERE s.tiendaId = :tiendaId
              AND s.estado <> shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion.CANCELADA
              AND s.eliminado = FALSE
            """)
    Optional<SuscripcionTiendaEntidad> buscarNoCanceladaPorTienda(@Param("tiendaId") UUID tiendaId);

    /** Mismo criterio que {@link #buscarNoCanceladaPorTienda}, bloqueada para escribir (PAG-010, T1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM SuscripcionTiendaEntidad s
            WHERE s.tiendaId = :tiendaId
              AND s.estado <> shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion.CANCELADA
              AND s.eliminado = FALSE
            """)
    Optional<SuscripcionTiendaEntidad> bloquearNoCanceladaPorTienda(@Param("tiendaId") UUID tiendaId);

    /** Bloquea una suscripción concreta para escribir (T2 del cobro). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SuscripcionTiendaEntidad s WHERE s.id = :id")
    Optional<SuscripcionTiendaEntidad> bloquearPorId(@Param("id") UUID id);

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

    /** Productos no eliminados de la tienda (uso frente a SUS-008 y SUS-011). */
    @Query(value = "SELECT count(*) FROM \"Productos\" WHERE \"TiendaId\" = :tiendaId AND \"Eliminado\" = FALSE",
            nativeQuery = true)
    long contarProductos(@Param("tiendaId") UUID tiendaId);

    /** Personal no eliminado sin contar al propietario (SUS-008). */
    @Query(value = """
            SELECT count(*) FROM "PersonalTienda" p
            JOIN "Tiendas" t ON t."Id" = p."TiendaId"
            WHERE p."TiendaId" = :tiendaId AND p."Eliminado" = FALSE AND p."UsuarioId" <> t."PropietarioId"
            """, nativeQuery = true)
    long contarPersonalSinPropietario(@Param("tiendaId") UUID tiendaId);

    /** Listado de la plataforma (solo lectura, paginado). {@code estado} nulo no filtra. */
    @Query(value = """
            SELECT s."TiendaId" AS "tiendaId", t."Subdominio" AS "subdominio", p."Codigo" AS "codigoPlan",
                   s."Estado" AS "estado", s."FinPeriodoActual" AS "finPeriodoActual"
            FROM "SuscripcionesTienda" s
            JOIN "Tiendas" t ON t."Id" = s."TiendaId"
            JOIN "PlanesSuscripcion" p ON p."Id" = s."PlanId"
            WHERE s."Eliminado" = FALSE AND (CAST(:estado AS VARCHAR) IS NULL OR s."Estado" = :estado)
            ORDER BY s."FechaCreacion" DESC
            """,
            countQuery = """
            SELECT count(*) FROM "SuscripcionesTienda" s
            WHERE s."Eliminado" = FALSE AND (CAST(:estado AS VARCHAR) IS NULL OR s."Estado" = :estado)
            """,
            nativeQuery = true)
    Page<SuscripcionPlataformaVista> buscarParaPlataforma(@Param("estado") String estado, Pageable pagina);
}
