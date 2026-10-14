package shopify.api.core.modulos.totales.modelo;

import java.math.BigDecimal;

/** Tarifa de envío elegida; {@code montoMinimoGratis} nulo = nunca gratis por monto (ENV-002). */
public record EnvioCalculo(BigDecimal precio, BigDecimal montoMinimoGratis) {
}
