package shopify.api.core.modulos.carrito;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.comun.campana.Campana;
import shopify.api.core.comun.campana.PrecioCampana;
import shopify.api.core.modulos.carrito.servicio.PreciosVenta;
import shopify.api.core.modulos.carrito.servicio.PreciosVenta.Linea;
import shopify.api.core.modulos.carrito.servicio.PreciosVenta.Resultado;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.totales.modelo.DescuentoCalculo;
import shopify.api.core.modulos.totales.modelo.TipoDescuentoAplicado;

/** R-028: campaña y cupón no se acumulan; gana el mayor descuento y un cupón de envío gratis convive con la campaña. */
class PreciosVentaTest {

    private static VarianteVendible variante(String lista, boolean conCampana) {
        BigDecimal precioLista = new BigDecimal(lista);
        PrecioCampana p = conCampana ? PrecioCampana.con(Campana.BLACK_FRIDAY, precioLista) : PrecioCampana.sinCampana(precioLista);
        return new VarianteVendible(UUID.randomUUID(), UUID.randomUUID(), "Producto", "Por defecto", "SKU", p.precioFinal(),
                true, null, true, p.precioLista(), p.porcentaje(), p.campana());
    }

    private static DescuentoCalculo porcentaje(String valor) {
        return new DescuentoCalculo(TipoDescuentoAplicado.PORCENTAJE, new BigDecimal(valor));
    }

    @Test
    void sinCampanaElCuponSigueComoSiempre() {
        List<Linea> lineas = List.of(new Linea(variante("100.00", false), 2));

        Resultado r = PreciosVenta.resolver(lineas, porcentaje("10"));

        assertThat(r.descuento()).isNotNull();
        assertThat(r.cuponIgnorado()).isFalse();
        assertThat(r.ahorroCampana()).isEqualByComparingTo("0.00");
        assertThat(r.precioUnitario(lineas.get(0).variante())).isEqualByComparingTo("100.00");
    }

    @Test
    void conCampanaYSinCuponSeVendeAPrecioDeCampana() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 2));

        Resultado r = PreciosVenta.resolver(lineas, null);

        assertThat(r.usaCampana()).isTrue();
        assertThat(r.descuento()).isNull();
        assertThat(r.ahorroCampana()).isEqualByComparingTo("60.00");
        assertThat(r.precioUnitario(lineas.get(0).variante())).isEqualByComparingTo("120.00");
        assertThat(r.conCampana(lineas.get(0).variante())).isTrue();
    }

    @Test
    void ganaElCuponSiDescuentaMasQueLaCampana() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 2)); // campaña ahorra 60 de 300

        Resultado r = PreciosVenta.resolver(lineas, porcentaje("50")); // el cupón ahorra 150

        assertThat(r.usaCampana()).isFalse();
        assertThat(r.descuento()).isNotNull();
        assertThat(r.cuponIgnorado()).isFalse();
        assertThat(r.ahorroCampana()).isEqualByComparingTo("0.00");
        assertThat(r.precioUnitario(lineas.get(0).variante())).isEqualByComparingTo("150.00");
        assertThat(r.conCampana(lineas.get(0).variante())).isFalse();
    }

    @Test
    void ganaLaCampanaSiDescuentaMasQueElCuponYElCuponSeIgnora() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 2));

        Resultado r = PreciosVenta.resolver(lineas, porcentaje("5")); // el cupón ahorra 15 contra 60 de la campaña

        assertThat(r.usaCampana()).isTrue();
        assertThat(r.descuento()).isNull();
        assertThat(r.cuponIgnorado()).isTrue();
        assertThat(r.ahorroCampana()).isEqualByComparingTo("60.00");
    }

    @Test
    void enUnEmpateGanaLaCampanaYElCuponNoSeConsume() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 2)); // 60 de ahorro

        Resultado r = PreciosVenta.resolver(lineas, new DescuentoCalculo(TipoDescuentoAplicado.MONTO_FIJO, new BigDecimal("60.00")));

        assertThat(r.cuponIgnorado()).isTrue();
        assertThat(r.usaCampana()).isTrue();
    }

    @Test
    void unMontoFijoMayorQueElSubtotalDescuentaComoMaximoElSubtotal() {
        List<Linea> lineas = List.of(new Linea(variante("50.00", true), 1)); // campaña ahorra 5

        Resultado r = PreciosVenta.resolver(lineas, new DescuentoCalculo(TipoDescuentoAplicado.MONTO_FIJO, new BigDecimal("500.00")));

        assertThat(r.usaCampana()).isFalse();
        assertThat(r.descuento()).isNotNull();
    }

    @Test
    void elEnvioGratisConviveConLaCampana() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 1));
        DescuentoCalculo envio = new DescuentoCalculo(TipoDescuentoAplicado.ENVIO_GRATIS, BigDecimal.ZERO);

        Resultado r = PreciosVenta.resolver(lineas, envio);

        assertThat(r.usaCampana()).isTrue();
        assertThat(r.descuento()).isSameAs(envio);
        assertThat(r.cuponIgnorado()).isFalse();
        assertThat(r.ahorroCampana()).isEqualByComparingTo("30.00");
    }

    @Test
    void elSubtotalDeListaSumaLosPreciosOriginales() {
        List<Linea> lineas = List.of(new Linea(variante("150.00", true), 2), new Linea(variante("50.00", true), 3));

        assertThat(PreciosVenta.subtotalLista(lineas)).isEqualByComparingTo("450.00");
    }
}
