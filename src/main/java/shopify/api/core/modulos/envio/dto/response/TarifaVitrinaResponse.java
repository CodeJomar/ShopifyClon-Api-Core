package shopify.api.core.modulos.envio.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Tarifa pública: sin estado ni orden internos (ENV-001). */
public record TarifaVitrinaResponse(
        UUID id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        BigDecimal montoMinimoGratis) {
}
