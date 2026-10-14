package shopify.api.core.modulos.envio.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Tarifa tal como la ve la tienda en el panel. */
public record TarifaEnvioResponse(
        UUID id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        BigDecimal montoMinimoGratis,
        Boolean estaActiva,
        Integer orden) {
}
