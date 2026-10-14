package shopify.api.core.modulos.balance.repositorio;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shopify.api.core.modulos.balance.entidad.BalanceTiendaEntidad;

public interface BalanceTiendaRepositorio extends JpaRepository<BalanceTiendaEntidad, UUID> {

    boolean existsByTiendaId(UUID tiendaId);

    Optional<BalanceTiendaEntidad> findByTiendaId(UUID tiendaId);

    /** BAL-001: incrementos relativos, sin leer el saldo antes (dos pagos a la vez no se pisan). */
    @Modifying
    @Query("UPDATE BalanceTiendaEntidad b SET b.totalVentasHistorico = b.totalVentasHistorico + :total, "
            + "b.totalComisiones = b.totalComisiones + :comision, b.cantidadOrdenes = b.cantidadOrdenes + 1, "
            + "b.saldoPendienteCobro = b.saldoPendienteCobro + :saldo WHERE b.tiendaId = :tiendaId")
    int sumarVenta(@Param("tiendaId") UUID tiendaId, @Param("total") BigDecimal total,
                   @Param("comision") BigDecimal comision, @Param("saldo") BigDecimal saldo);

    /** BAL-002: el reembolso resta del saldo; la comisión no se devuelve en v1 (Q-014). */
    @Modifying
    @Query("UPDATE BalanceTiendaEntidad b SET b.totalReembolsado = b.totalReembolsado + :monto, "
            + "b.saldoPendienteCobro = b.saldoPendienteCobro - :monto WHERE b.tiendaId = :tiendaId")
    int sumarReembolso(@Param("tiendaId") UUID tiendaId, @Param("monto") BigDecimal monto);
}
