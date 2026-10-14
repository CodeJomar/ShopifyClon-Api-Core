package shopify.api.core.modulos.tienda.modelo;

import java.math.BigDecimal;

/** Moneda, tasa de impuesto y si los precios ya lo incluyen (D-012). */
public record ConfiguracionVenta(String moneda, BigDecimal tasaImpuesto, Boolean preciosIncluyenImpuesto) {
}
