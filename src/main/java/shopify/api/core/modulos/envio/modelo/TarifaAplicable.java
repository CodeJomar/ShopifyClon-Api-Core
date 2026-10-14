package shopify.api.core.modulos.envio.modelo;

import java.math.BigDecimal;
import java.util.UUID;

/** Tarifa activa de la tienda que el checkout puede cobrar (ENV-001, ENV-002). */
public record TarifaAplicable(UUID id, String nombre, BigDecimal precio, BigDecimal montoMinimoGratis) {
}
