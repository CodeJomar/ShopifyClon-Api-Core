package shopify.api.core.modulos.envio.dto.response;

import java.time.Instant;
import java.util.UUID;
import shopify.api.core.modulos.envio.modelo.EstadoEnvio;

/** Envío de una orden, con sus fechas de despacho y entrega. */
public record EnvioResponse(
        UUID id,
        UUID ordenId,
        String empresaTransporte,
        String numeroGuia,
        String urlSeguimiento,
        EstadoEnvio estado,
        Instant fechaDespacho,
        Instant fechaEntregaEstimada,
        Instant fechaEntregaReal) {
}
