package shopify.api.core.comun.campana;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Tramos de descuento de las campañas (R-028): fronteras exactas y redondeo a céntimos. */
class TramosCampanaTest {

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void elPorcentajeCambiaExactamenteEnCadaUmbral() {
        assertThat(TramosCampana.porcentajePara(d("0.00"))).isEqualTo(10);
        assertThat(TramosCampana.porcentajePara(d("99.99"))).isEqualTo(10);
        assertThat(TramosCampana.porcentajePara(d("100.00"))).isEqualTo(20);
        assertThat(TramosCampana.porcentajePara(d("499.99"))).isEqualTo(20);
        assertThat(TramosCampana.porcentajePara(d("500.00"))).isEqualTo(25);
        assertThat(TramosCampana.porcentajePara(d("999.99"))).isEqualTo(25);
        assertThat(TramosCampana.porcentajePara(d("1000.00"))).isEqualTo(30);
        assertThat(TramosCampana.porcentajePara(d("25000.00"))).isEqualTo(30);
    }

    @Test
    void elPrecioFinalSeRedondeaACentimosHalfUp() {
        assertThat(TramosCampana.precioFinal(d("50.00"))).isEqualByComparingTo("45.00");
        assertThat(TramosCampana.precioFinal(d("99.99"))).isEqualByComparingTo("89.99");
        assertThat(TramosCampana.precioFinal(d("100.00"))).isEqualByComparingTo("80.00");
        assertThat(TramosCampana.precioFinal(d("499.99"))).isEqualByComparingTo("399.99");
        assertThat(TramosCampana.precioFinal(d("500.00"))).isEqualByComparingTo("375.00");
        assertThat(TramosCampana.precioFinal(d("1000.00"))).isEqualByComparingTo("700.00");
        assertThat(TramosCampana.precioFinal(d("0.05"))).isEqualByComparingTo("0.05");
        assertThat(TramosCampana.precioFinal(d("0.00"))).isEqualByComparingTo("0.00");
        assertThat(TramosCampana.precioFinal(d("10.00")).scale()).isEqualTo(2);
    }

    @Test
    void unPrecioJustoSobreUnUmbralPuedeQuedarMasBaratoQueUnoJustoDebajo() {
        // Es una propiedad del escalonado pedido: 100 con 20 % (80) cuesta menos que 99,99 con 10 % (89,99)
        assertThat(TramosCampana.precioFinal(d("100.00"))).isLessThan(TramosCampana.precioFinal(d("99.99")));
    }

    @Test
    void lasCampanasSeBuscanPorCodigoSinDistinguirMayusculas() {
        assertThat(Campana.de("black_friday")).contains(Campana.BLACK_FRIDAY);
        assertThat(Campana.de(" NAVIDAD ")).contains(Campana.NAVIDAD);
        assertThat(Campana.de("DIA_DE_LA_MADRE").orElseThrow().nombre()).isEqualTo("Día de la Madre");
        assertThat(Campana.de("INVENTADA")).isEmpty();
        assertThat(Campana.de(null)).isEmpty();
        assertThat(Campana.de("  ")).isEmpty();
        assertThat(Campana.values()).hasSize(5);
    }

    @Test
    void elPrecioConCampanaGuardaListaFinalPorcentajeYCodigo() {
        PrecioCampana precio = PrecioCampana.con(Campana.HALLOWEEN, d("150.00"));

        assertThat(precio.precioLista()).isEqualByComparingTo("150.00");
        assertThat(precio.precioFinal()).isEqualByComparingTo("120.00");
        assertThat(precio.porcentaje()).isEqualTo(20);
        assertThat(precio.campana()).isEqualTo("HALLOWEEN");
        assertThat(precio.hayCampana()).isTrue();
        assertThat(precio.ahorroUnitario()).isEqualByComparingTo("30.00");

        PrecioCampana sin = PrecioCampana.sinCampana(d("150.00"));
        assertThat(sin.hayCampana()).isFalse();
        assertThat(sin.precioFinal()).isEqualByComparingTo("150.00");
        assertThat(sin.ahorroUnitario()).isEqualByComparingTo("0.00");
    }
}
