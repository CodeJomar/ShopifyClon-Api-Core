package shopify.api.core.modulos.inventario.repositorio;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.inventario.entidad.MovimientoInventarioEntidad;

public interface MovimientoInventarioRepositorio extends JpaRepository<MovimientoInventarioEntidad, UUID> {

    /**
     * Historial de una variante entre {@code desde} y {@code hasta}. Los límites llegan siempre con valor
     * (el servicio pone los extremos cuando no hay filtro): un nulo tipado no lo resuelve PostgreSQL.
     */
    @Query("""
            SELECT m FROM MovimientoInventarioEntidad m
            WHERE m.varianteId = :varianteId AND m.tiendaId = :tiendaId
              AND m.fechaCreacion >= :desde AND m.fechaCreacion <= :hasta
            """)
    Page<MovimientoInventarioEntidad> historial(@Param("tiendaId") UUID tiendaId,
                                                @Param("varianteId") UUID varianteId,
                                                @Param("desde") Instant desde,
                                                @Param("hasta") Instant hasta,
                                                Pageable pagina);
}
