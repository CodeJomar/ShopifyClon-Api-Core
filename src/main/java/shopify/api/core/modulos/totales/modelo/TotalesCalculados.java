package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado del cálculo, todo con escala 2. {@code envioPendiente} indica que la orden requiere envío pero
 * todavía no se eligió tarifa (el costo es 0.00 hasta entonces).
 */
public record TotalesCalculados(
        List<BigDecimal> totalesLinea,
        BigDecimal subtotal,
        BigDecimal descuentoTotal,
        BigDecimal costoEnvio,
        BigDecimal impuestosTotal,
        BigDecimal total,
        boolean requiereEnvio,
        boolean envioPendiente) {
}
