package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;

/** Tasa de impuesto (0.18 = 18 %) y si ya viene incluida en los precios (D-012). */
public record ImpuestoCalculo(BigDecimal tasa, boolean incluidoEnPrecio) {
}
