package shopify.api.core.comun.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Aritmética de dinero: escala 2 y {@code HALF_UP}. */
public final class Dinero {

    public static final int ESCALA = 2;
    public static final RoundingMode REDONDEO = RoundingMode.HALF_UP;
    public static final BigDecimal CERO = BigDecimal.ZERO.setScale(ESCALA, REDONDEO);

    private Dinero() {
    }

    public static BigDecimal redondear(BigDecimal valor) {
        return requerido(valor).setScale(ESCALA, REDONDEO);
    }

    public static BigDecimal multiplicar(BigDecimal precio, int cantidad) {
        return redondear(requerido(precio).multiply(BigDecimal.valueOf(cantidad)));
    }

    /** Aplica una tasa a una base; {@code tasa} 0.18 equivale a 18 %. */
    public static BigDecimal porcentaje(BigDecimal base, BigDecimal tasa) {
        return redondear(requerido(base).multiply(requerido(tasa)));
    }

    public static boolean esPositivo(BigDecimal valor) {
        return requerido(valor).signum() > 0;
    }

    private static BigDecimal requerido(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("El importe no puede ser nulo");
        }
        return valor;
    }
}
