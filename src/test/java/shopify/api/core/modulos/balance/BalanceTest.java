package shopify.api.core.modulos.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.balance.servicio.BalanceServicio;
import shopify.api.core.soporte.PruebaIntegracionBase;

class BalanceTest extends PruebaIntegracionBase {

    @Autowired
    private BalanceServicio balances;

    @Autowired
    private TransactionTemplate transaccion;

    @Test
    void crearSiNoExisteDosVecesDejaUnaSolaFilaEnCero() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        transaccion.executeWithoutResult(estado -> balances.crearSiNoExiste(tienda));
        transaccion.executeWithoutResult(estado -> balances.crearSiNoExiste(tienda));

        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", Integer.class, tienda);
        assertThat(filas).isEqualTo(1);

        Map<String, Object> fila = jdbc.queryForMap("SELECT \"TotalVentasHistorico\", \"CantidadOrdenes\", "
                + "\"SaldoPendienteCobro\", \"TotalReembolsado\", \"TotalComisiones\" "
                + "FROM \"BalancesTienda\" WHERE \"TiendaId\" = ?", tienda);
        assertThat((BigDecimal) fila.get("TotalVentasHistorico")).isEqualByComparingTo("0.00");
        assertThat(fila.get("CantidadOrdenes")).isEqualTo(0);
        assertThat((BigDecimal) fila.get("SaldoPendienteCobro")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) fila.get("TotalReembolsado")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) fila.get("TotalComisiones")).isEqualByComparingTo("0.00");
    }

    @Test
    void crearSiNoExisteFueraDeTransaccionFalla() {
        UUID tienda = crearTienda(crearUsuarioActivo(false));

        assertThatThrownBy(() -> balances.crearSiNoExiste(tienda))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
