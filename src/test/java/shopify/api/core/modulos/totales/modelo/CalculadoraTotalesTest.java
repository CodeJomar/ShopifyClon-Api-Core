package shopify.api.core.modulos.totales.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Los 8 casos de T-012 con valores exactos (escala 2) y las validaciones de la regla 1. */
class CalculadoraTotalesTest {

    private static BigDecimal d(String valor) {
        return new BigDecimal(valor);
    }

    private static LineaCalculo linea(String precio, int cantidad, boolean requiereEnvio) {
        return new LineaCalculo(d(precio), cantidad, requiereEnvio);
    }

    private static void verificar(TotalesCalculados t, String subtotal, String descuento, String envio,
                                  String impuestos, String total) {
        assertEquals(d(subtotal), t.subtotal(), "subtotal");
        assertEquals(d(descuento), t.descuentoTotal(), "descuento");
        assertEquals(d(envio), t.costoEnvio(), "envío");
        assertEquals(d(impuestos), t.impuestosTotal(), "impuestos");
        assertEquals(d(total), t.total(), "total");
    }

    /** Escala 2 en todos los importes, no solo en el valor numérico. */
    private static void assertEscalaDos(TotalesCalculados t) {
        assertEquals(2, t.subtotal().scale());
        assertEquals(2, t.descuentoTotal().scale());
        assertEquals(2, t.costoEnvio().scale());
        assertEquals(2, t.impuestosTotal().scale());
        assertEquals(2, t.total().scale());
        t.totalesLinea().forEach(importe -> assertEquals(2, importe.scale()));
    }

    private static List<LineaCalculo> ordenDeEjemplo() {
        List<LineaCalculo> lineas = new ArrayList<>();
        lineas.add(linea("50.00", 2, true));
        lineas.add(linea("19.90", 1, true));
        return lineas;
    }

    @Test
    void caso1PorcentajeConImpuestoIncluido() {
        TotalesCalculados t = CalculadoraTotales.calcular(ordenDeEjemplo(),
                new DescuentoCalculo(TipoDescuentoAplicado.PORCENTAJE, d("10")),
                new EnvioCalculo(d("15.00"), d("150.00")),
                new ImpuestoCalculo(d("0.18"), true));

        verificar(t, "119.90", "11.99", "15.00", "16.46", "122.91");
        assertEquals(List.of(d("100.00"), d("19.90")), t.totalesLinea());
        assertEscalaDos(t);
    }

    @Test
    void caso2PorcentajeConImpuestoNoIncluido() {
        TotalesCalculados t = CalculadoraTotales.calcular(ordenDeEjemplo(),
                new DescuentoCalculo(TipoDescuentoAplicado.PORCENTAJE, d("10")),
                new EnvioCalculo(d("15.00"), d("150.00")),
                new ImpuestoCalculo(d("0.18"), false));

        verificar(t, "119.90", "11.99", "15.00", "19.42", "142.33");
        assertEscalaDos(t);
    }

    @Test
    void caso3MontoFijoMayorAlSubtotalSeTopaEnElSubtotal() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("30.00", 1, true)),
                new DescuentoCalculo(TipoDescuentoAplicado.MONTO_FIJO, d("50.00")),
                new EnvioCalculo(d("10.00"), null),
                new ImpuestoCalculo(d("0.18"), true));

        verificar(t, "30.00", "30.00", "10.00", "0.00", "10.00");
        assertEscalaDos(t);
    }

    @Test
    void caso4EnvioGratisPorCupon() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("100.00", 1, true)),
                new DescuentoCalculo(TipoDescuentoAplicado.ENVIO_GRATIS, d("0")),
                new EnvioCalculo(d("20.00"), null),
                new ImpuestoCalculo(d("0.18"), true));

        verificar(t, "100.00", "0.00", "0.00", "15.25", "100.00");
        assertEscalaDos(t);
    }

    @Test
    void caso5EnvioGratisPorMontoMinimo() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("60.00", 3, true)),
                null,
                new EnvioCalculo(d("20.00"), d("150.00")),
                new ImpuestoCalculo(d("0"), true));

        verificar(t, "180.00", "0.00", "0.00", "0.00", "180.00");
        assertEscalaDos(t);
    }

    @Test
    void caso6SinEnvioNoLlevaCostoDeEnvio() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("9.99", 1, false)),
                null,
                null,
                new ImpuestoCalculo(d("0.18"), false));

        verificar(t, "9.99", "0.00", "0.00", "1.80", "11.79");
        assertFalse(t.requiereEnvio());
        assertFalse(t.envioPendiente());
        assertEscalaDos(t);
    }

    @Test
    void caso7PorcentajeRedondeaYLaBaseSigueConEnvio() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("33.33", 3, true)),
                new DescuentoCalculo(TipoDescuentoAplicado.PORCENTAJE, d("15")),
                new EnvioCalculo(d("5.00"), null),
                new ImpuestoCalculo(d("0"), true));

        verificar(t, "99.99", "15.00", "5.00", "0.00", "89.99");
        assertEscalaDos(t);
    }

    @Test
    void caso8EnvioPendienteCuandoNoSeEligioTarifa() {
        TotalesCalculados t = CalculadoraTotales.calcular(List.of(linea("40.00", 1, true)),
                null,
                null,
                new ImpuestoCalculo(d("0.18"), true));

        verificar(t, "40.00", "0.00", "0.00", "6.10", "40.00");
        assertTrue(t.requiereEnvio());
        assertTrue(t.envioPendiente());
        assertEscalaDos(t);
    }

    @Test
    void validacionesLanzanIllegalArgumentException() {
        ImpuestoCalculo impuesto = new ImpuestoCalculo(d("0.18"), true);

        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(), null, null, impuesto));
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(null, null, null, impuesto));

        List<LineaCalculo> demasiadas = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            demasiadas.add(linea("1.00", 1, false));
        }
        assertThrows(IllegalArgumentException.class, () -> CalculadoraTotales.calcular(demasiadas, null, null, impuesto));

        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(linea("1.00", 0, false)), null, null, impuesto));
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(linea("1.00", 100, false)), null, null, impuesto));
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(linea("-1.00", 1, false)), null, null, impuesto));
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(linea("1.00", 1, false)), null, null,
                        new ImpuestoCalculo(d("0.60"), true)));
        assertThrows(IllegalArgumentException.class,
                () -> CalculadoraTotales.calcular(List.of(linea("1.00", 1, false)), null, null, null));
    }
}
