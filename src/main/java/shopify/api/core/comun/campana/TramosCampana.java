package shopify.api.core.comun.campana;

import java.math.BigDecimal;
import java.util.List;
import shopify.api.core.comun.util.Dinero;

/**
 * Descuento escalonado de las campañas según el precio unitario de lista del producto (en la moneda de la tienda).
 * Cálculo puro. Los tramos son constantes fáciles de cambiar: cada uno aplica desde su umbral (inclusive).
 */
public final class TramosCampana {

    /** Un tramo: desde ese precio de lista (inclusive) se aplica ese porcentaje. */
    public record Tramo(BigDecimal desde, int porcentaje) {
    }

    /** En orden ascendente por umbral. Menos de 100 → 10 %; desde 100 → 20 %; desde 500 → 25 %; desde 1000 → 30 %. */
    public static final List<Tramo> TRAMOS = List.of(
            new Tramo(new BigDecimal("0"), 10),
            new Tramo(new BigDecimal("100"), 20),
            new Tramo(new BigDecimal("500"), 25),
            new Tramo(new BigDecimal("1000"), 30));

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private TramosCampana() {
    }

    /** Porcentaje (entero) que corresponde a un precio unitario de lista. */
    public static int porcentajePara(BigDecimal precioLista) {
        if (precioLista == null) {
            throw new IllegalArgumentException("El precio no puede ser nulo");
        }
        int porcentaje = TRAMOS.get(0).porcentaje();
        for (Tramo tramo : TRAMOS) {
            if (precioLista.compareTo(tramo.desde()) >= 0) {
                porcentaje = tramo.porcentaje();
            }
        }
        return porcentaje;
    }

    /** Precio unitario con la campaña aplicada: escala 2, {@code HALF_UP}, igual que el resto del dinero. */
    public static BigDecimal precioFinal(BigDecimal precioLista) {
        int porcentaje = porcentajePara(precioLista);
        return Dinero.redondear(precioLista.multiply(BigDecimal.valueOf(100L - porcentaje)).divide(CIEN));
    }
}
