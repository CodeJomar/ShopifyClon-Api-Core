package shopify.api.core.modulos.balance.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"BalancesTienda\"")
public class BalanceTiendaEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"TotalVentasHistorico\"", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVentasHistorico = BigDecimal.ZERO;

    @Column(name = "\"CantidadOrdenes\"", nullable = false)
    private Integer cantidadOrdenes = 0;

    @Column(name = "\"SaldoPendienteCobro\"", nullable = false, precision = 14, scale = 2)
    private BigDecimal saldoPendienteCobro = BigDecimal.ZERO;

    @Column(name = "\"TotalReembolsado\"", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalReembolsado = BigDecimal.ZERO;

    @Column(name = "\"TotalComisiones\"", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalComisiones = BigDecimal.ZERO;
}
