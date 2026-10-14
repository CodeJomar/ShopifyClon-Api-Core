package shopify.api.core.modulos.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** BAL-001 y BAL-002: asientos del balance por tienda. */
class BalanceServicioTest extends PruebaIntegracionBase {

    @Autowired
    private BalanceServicio balance;

    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void lasVentasAcumulanTotalComisionCantidadYSaldo() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        transaccion.executeWithoutResult(estado -> balance.registrarVenta(tienda, new BigDecimal("100.00"), new BigDecimal("2.00")));
        transaccion.executeWithoutResult(estado -> balance.registrarVenta(tienda, new BigDecimal("50.00"), new BigDecimal("1.00")));

        assertThat(valor(tienda, "TotalVentasHistorico")).isEqualByComparingTo("150.00");
        assertThat(valor(tienda, "TotalComisiones")).isEqualByComparingTo("3.00");
        assertThat(valor(tienda, "SaldoPendienteCobro")).isEqualByComparingTo("147.00");
        assertThat(jdbc.queryForObject("SELECT \"CantidadOrdenes\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                Integer.class, tienda)).isEqualTo(2);
    }

    @Test
    void elReembolsoRestaDelSaldoYSeSuma() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        transaccion.executeWithoutResult(estado -> balance.registrarVenta(tienda, new BigDecimal("100.00"), new BigDecimal("2.00")));

        transaccion.executeWithoutResult(estado -> balance.registrarReembolso(tienda, new BigDecimal("30.00")));

        assertThat(valor(tienda, "TotalReembolsado")).isEqualByComparingTo("30.00");
        assertThat(valor(tienda, "SaldoPendienteCobro")).isEqualByComparingTo("68.00");
        // La comisión no se devuelve en v1 (Q-014)
        assertThat(valor(tienda, "TotalComisiones")).isEqualByComparingTo("2.00");
    }

    @Test
    void siLaTiendaNoTeniaBalanceLoCrea() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                Integer.class, tienda)).isZero();

        transaccion.executeWithoutResult(estado -> balance.registrarVenta(tienda, new BigDecimal("10.00"), BigDecimal.ZERO));

        assertThat(valor(tienda, "TotalVentasHistorico")).isEqualByComparingTo("10.00");
    }

    @Test
    void registrarFueraDeUnaTransaccionFalla() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        assertThatThrownBy(() -> balance.registrarVenta(tienda, BigDecimal.TEN, BigDecimal.ZERO))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private BigDecimal valor(UUID tienda, String columna) {
        return jdbc.queryForObject("SELECT \"" + columna + "\" FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?",
                BigDecimal.class, tienda);
    }
}
