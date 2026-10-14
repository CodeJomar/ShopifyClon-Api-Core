package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;

/** Descuento aplicado al subtotal; el valor depende del tipo (porcentaje, monto fijo o 0 para envío gratis). */
public record DescuentoCalculo(TipoDescuentoAplicado tipo, BigDecimal valor) {
}
