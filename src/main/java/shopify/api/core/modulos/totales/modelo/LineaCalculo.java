package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;

/** Una línea de la orden: precio unitario, cantidad (1–99) y si el producto requiere envío. */
public record LineaCalculo(BigDecimal precioUnitario, int cantidad, boolean requiereEnvio) {
}
