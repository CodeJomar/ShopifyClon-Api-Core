package shopify.api.core.modulos.tienda.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Configuración completa de la tienda para el panel; no expone el siguiente número de orden. */
public record TiendaConfiguracionResponse(
        UUID id,
        String nombre,
        String subdominio,
        String dominioPersonalizado,
        String moneda,
        String correoContacto,
        BigDecimal tasaImpuesto,
        boolean preciosIncluyenImpuesto,
        String zonaHoraria,
        boolean estaActiva,
        boolean operativa,
        SuscripcionResumenResponse suscripcion,
        Instant fechaCreacion) {
}
