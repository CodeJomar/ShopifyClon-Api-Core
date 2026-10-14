package shopify.api.core.modulos.orden.repositorio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.orden.entidad.OrdenEntidad;
import shopify.api.core.modulos.orden.modelo.EstadoOrden;
import shopify.api.core.modulos.orden.modelo.EstadoPago;
import shopify.api.core.modulos.orden.modelo.EstadoPreparacion;

/**
 * Solo lectura sobre {@code Ordenes} para el panel, el comprador y la vitrina (ORD-010 a ORD-012). Los filtros no
 * reciben nulos: cada uno lleva un indicador booleano y un valor de relleno, así PostgreSQL siempre recibe el tipo.
 */
public interface OrdenConsultaRepositorio extends JpaRepository<OrdenEntidad, UUID> {

    Optional<OrdenEntidad> findFirstByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    Optional<OrdenEntidad> findFirstByTokenAccesoAndTiendaIdAndEliminadoFalse(String tokenAcceso, UUID tiendaId);

    @Query(value = """
            SELECT o FROM OrdenEntidad o
            WHERE o.tiendaId = :tiendaId AND o.eliminado = FALSE
              AND (:conEstado = FALSE OR o.estado = :estado)
              AND (:conEstadoPago = FALSE OR o.estadoPago = :estadoPago)
              AND (:conPreparacion = FALSE OR o.estadoPreparacion = :preparacion)
              AND o.fechaCreacion >= :desde AND o.fechaCreacion <= :hasta
              AND (LOWER(o.numeroOrden) LIKE :patron ESCAPE '!' OR LOWER(o.correoCliente) LIKE :patron ESCAPE '!'
                   OR LOWER(o.nombreCliente) LIKE :patron ESCAPE '!')
            """,
            countQuery = """
            SELECT COUNT(o) FROM OrdenEntidad o
            WHERE o.tiendaId = :tiendaId AND o.eliminado = FALSE
              AND (:conEstado = FALSE OR o.estado = :estado)
              AND (:conEstadoPago = FALSE OR o.estadoPago = :estadoPago)
              AND (:conPreparacion = FALSE OR o.estadoPreparacion = :preparacion)
              AND o.fechaCreacion >= :desde AND o.fechaCreacion <= :hasta
              AND (LOWER(o.numeroOrden) LIKE :patron ESCAPE '!' OR LOWER(o.correoCliente) LIKE :patron ESCAPE '!'
                   OR LOWER(o.nombreCliente) LIKE :patron ESCAPE '!')
            """)
    Page<OrdenEntidad> buscarEnTienda(@Param("tiendaId") UUID tiendaId,
                                      @Param("conEstado") boolean conEstado, @Param("estado") EstadoOrden estado,
                                      @Param("conEstadoPago") boolean conEstadoPago,
                                      @Param("estadoPago") EstadoPago estadoPago,
                                      @Param("conPreparacion") boolean conPreparacion,
                                      @Param("preparacion") EstadoPreparacion preparacion,
                                      @Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                      @Param("patron") String patron, Pageable pagina);

    /** Historial del comprador registrado: sus órdenes de todas las tiendas, o de una si se filtra. */
    @Query(value = """
            SELECT o FROM OrdenEntidad o
            WHERE o.usuarioId = :usuarioId AND o.eliminado = FALSE
              AND (:filtrarTienda = FALSE OR o.tiendaId = :tiendaId)
            """,
            countQuery = """
            SELECT COUNT(o) FROM OrdenEntidad o
            WHERE o.usuarioId = :usuarioId AND o.eliminado = FALSE
              AND (:filtrarTienda = FALSE OR o.tiendaId = :tiendaId)
            """)
    Page<OrdenEntidad> historialDelUsuario(@Param("usuarioId") UUID usuarioId,
                                           @Param("filtrarTienda") boolean filtrarTienda,
                                           @Param("tiendaId") UUID tiendaId, Pageable pagina);

    Optional<OrdenEntidad> findFirstByIdAndUsuarioIdAndEliminadoFalse(UUID id, UUID usuarioId);
}
