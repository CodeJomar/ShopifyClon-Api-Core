package shopify.api.core.comun.campana;

import java.math.BigDecimal;
import shopify.api.core.comun.util.Dinero;

/**
 * Precio de una variante con la campaña vigente: el de lista, el final, y qué campaña lo rebajó. Sin campaña, el
 * precio final es el de lista y {@code campana} es nulo.
 */
public record PrecioCampana(BigDecimal precioLista, BigDecimal precioFinal, int porcentaje, String campana) {

    public static PrecioCampana sinCampana(BigDecimal precioLista) {
        return new PrecioCampana(precioLista, precioLista, 0, null);
    }

    public static PrecioCampana con(Campana campana, BigDecimal precioLista) {
        return new PrecioCampana(precioLista, TramosCampana.precioFinal(precioLista),
                TramosCampana.porcentajePara(precioLista), campana.name());
    }

    public boolean hayCampana() {
        return campana != null;
    }

    /** Lo que se ahorra por unidad (cero sin campaña). */
    public BigDecimal ahorroUnitario() {
        return Dinero.redondear(precioLista.subtract(precioFinal));
    }
}
