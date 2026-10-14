package shopify.api.core.modulos.cupon.repositorio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.cupon.entidad.CuponEntidad;

public interface CuponRepositorio extends JpaRepository<CuponEntidad, UUID> {

    Optional<CuponEntidad> findByIdAndTiendaIdAndEliminadoFalse(UUID id, UUID tiendaId);

    /** Los códigos se guardan en mayúsculas, así que la búsqueda por código es exacta. */
    Optional<CuponEntidad> findByTiendaIdAndCodigoAndEliminadoFalse(UUID tiendaId, String codigo);

    boolean existsByTiendaIdAndCodigoAndEliminadoFalse(UUID tiendaId, String codigo);

    boolean existsByTiendaIdAndCodigoAndEliminadoFalseAndIdNot(UUID tiendaId, String codigo, UUID id);

    /** {@code patron} es {@code %texto%} con los comodines escapados con {@code !}, o nulo para no filtrar. */
    @Query("""
            SELECT c FROM CuponEntidad c
            WHERE c.tiendaId = :tiendaId AND c.eliminado = FALSE
              AND (:activo IS NULL OR c.estaActivo = :activo)
              AND (:patron IS NULL OR c.codigo LIKE :patron ESCAPE '!')
            """)
    Page<CuponEntidad> buscar(@Param("tiendaId") UUID tiendaId,
                              @Param("patron") String patron,
                              @Param("activo") Boolean activo,
                              Pageable pagina);

    /**
     * CUP-007: consume un uso solo si el cupón sigue aplicable en el momento de escribir.
     * Devuelve 0 si ya no hay usos o si dejó de estar vigente.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CuponEntidad c SET c.cantidadUsosActual = c.cantidadUsosActual + 1
            WHERE c.id = :id AND c.eliminado = FALSE AND c.estaActivo = TRUE
              AND c.fechaInicio <= :ahora AND (c.fechaFin IS NULL OR c.fechaFin > :ahora)
              AND (c.limiteUsosTotal IS NULL OR c.cantidadUsosActual < c.limiteUsosTotal)
            """)
    int consumirUso(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /** Repone un uso sin bajar de cero. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE CuponEntidad c SET c.cantidadUsosActual = c.cantidadUsosActual - 1
            WHERE c.id = :id AND c.cantidadUsosActual > 0
            """)
    int devolverUso(@Param("id") UUID id);
}
