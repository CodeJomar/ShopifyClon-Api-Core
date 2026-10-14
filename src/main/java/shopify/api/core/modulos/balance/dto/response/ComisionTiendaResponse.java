package shopify.api.core.modulos.balance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Comisiones de una tienda para el administrador de plataforma. */
public record ComisionTiendaResponse(
        UUID tiendaId,
        String nombre,
        String subdominio,
        String planVigente,
        BigDecimal totalComisiones,
        BigDecimal totalVentasHistorico) {
}
