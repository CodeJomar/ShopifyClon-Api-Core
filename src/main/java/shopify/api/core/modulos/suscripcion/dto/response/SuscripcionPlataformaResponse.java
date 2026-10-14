package shopify.api.core.modulos.suscripcion.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Fila del listado de la plataforma. */
public record SuscripcionPlataformaResponse(
        UUID tiendaId,
        String subdominio,
        String codigoPlan,
        String estado,
        Instant finPeriodoActual) {
}
