package shopify.api.core.comun.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DineroTest {

    @Test
    void redondeaHalfUpADosDecimales() {
        assertThat(Dinero.redondear(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Dinero.redondear(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
        assertThat(Dinero.redondear(new BigDecimal("2.5"))).hasToString("2.50");
    }

    @Test
    void ceroTieneEscalaDos() {
        assertThat(Dinero.CERO).hasToString("0.00");
    }

    @Test
    void multiplicaPorCantidad() {
        assertThat(Dinero.multiplicar(new BigDecimal("19.99"), 3)).hasToString("59.97");
        assertThat(Dinero.multiplicar(new BigDecimal("0.335"), 3)).hasToString("1.01");
    }

    @Test
    void calculaPorcentaje() {
        assertThat(Dinero.porcentaje(new BigDecimal("100.00"), new BigDecimal("0.18"))).hasToString("18.00");
        assertThat(Dinero.porcentaje(new BigDecimal("9.99"), new BigDecimal("0.18"))).hasToString("1.80");
    }

    @Test
    void esPositivoSoloConMasQueCero() {
        assertThat(Dinero.esPositivo(new BigDecimal("0.01"))).isTrue();
        assertThat(Dinero.esPositivo(BigDecimal.ZERO)).isFalse();
        assertThat(Dinero.esPositivo(new BigDecimal("-1"))).isFalse();
    }

    @Test
    void nuloLanzaIllegalArgument() {
        assertThatThrownBy(() -> Dinero.redondear(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.multiplicar(null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.porcentaje(null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.porcentaje(BigDecimal.ONE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.esPositivo(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
