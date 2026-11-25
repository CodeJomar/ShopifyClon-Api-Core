package shopify.api.core.modulos.carrito.servicio;

import java.math.BigDecimal;
import java.util.List;
import shopify.api.core.comun.util.Dinero;
import shopify.api.core.modulos.catalogo.modelo.VarianteVendible;
import shopify.api.core.modulos.totales.modelo.DescuentoCalculo;
import shopify.api.core.modulos.totales.modelo.TipoDescuentoAplicado;

/**
 * Decide con qué precios se vende una tienda cuando hay campaña vigente y un cupón (R-028): no se acumulan, gana el
 * mayor descuento. Un cupón de envío gratis no compite (rebaja otra cosa) y convive con la campaña. La usan la
 * cotización del carrito y el checkout, así el total estimado es el que se cobra. Cálculo puro.
 */
public final class PreciosVenta {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private PreciosVenta() {
    }

    /** Una línea vendible: la variante (con precio final y de lista) y su cantidad. */
    public record Linea(VarianteVendible variante, int cantidad) {
    }

    /**
     * {@code usaCampana}: se venden a precio de campaña (si no hay campaña, es el precio de lista); si es falso gana el
     * cupón y se vende a precio de lista. {@code descuento} es el cupón que sigue aplicando (nulo si no hay o si perdió
     * contra la campaña). {@code ahorroCampana} es lo que la campaña rebaja en total (0 si no rige).
     */
    public record Resultado(boolean usaCampana, DescuentoCalculo descuento, boolean cuponIgnorado, BigDecimal ahorroCampana) {

        public BigDecimal precioUnitario(VarianteVendible variante) {
            return usaCampana ? variante.precio() : variante.precioLista();
        }

        /** La campaña rebaja de verdad el precio de esta variante en esta venta. */
        public boolean conCampana(VarianteVendible variante) {
            return usaCampana && variante.hayCampana();
        }
    }

    public static BigDecimal subtotalLista(List<Linea> lineas) {
        BigDecimal total = Dinero.CERO;
        for (Linea linea : lineas) {
            total = total.add(Dinero.multiplicar(linea.variante().precioLista(), linea.cantidad()));
        }
        return total;
    }

    private static BigDecimal subtotalCampana(List<Linea> lineas) {
        BigDecimal total = Dinero.CERO;
        for (Linea linea : lineas) {
            total = total.add(Dinero.multiplicar(linea.variante().precio(), linea.cantidad()));
        }
        return total;
    }

    public static Resultado resolver(List<Linea> lineas, DescuentoCalculo cupon) {
        BigDecimal lista = subtotalLista(lineas);
        BigDecimal ahorro = lista.subtract(subtotalCampana(lineas));
        if (ahorro.signum() <= 0) {
            return new Resultado(true, cupon, false, Dinero.CERO);
        }
        if (cupon == null || cupon.tipo() == TipoDescuentoAplicado.ENVIO_GRATIS) {
            return new Resultado(true, cupon, false, ahorro);
        }
        BigDecimal descuentoCupon = switch (cupon.tipo()) {
            case PORCENTAJE -> Dinero.redondear(lista.multiply(cupon.valor()).divide(CIEN));
            case MONTO_FIJO -> Dinero.redondear(cupon.valor().min(lista));
            case ENVIO_GRATIS -> Dinero.CERO;
        };
        if (descuentoCupon.compareTo(ahorro) > 0) {
            return new Resultado(false, cupon, false, Dinero.CERO);
        }
        return new Resultado(true, null, true, ahorro);
    }
}
