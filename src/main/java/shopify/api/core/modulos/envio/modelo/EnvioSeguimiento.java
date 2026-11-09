package shopify.api.core.modulos.envio.modelo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.envio.dto.response.EventoEnvioResponse;

/** Lo que se puede mostrar públicamente de un envío: sin comprador, dirección ni teléfono. */
public record EnvioSeguimiento(
        UUID envioId,
        String numeroGuia,
        EstadoEnvio estado,
        String empresaTransporte,
        Instant fechaDespacho,
        Instant fechaEntregaEstimada,
        Instant fechaEntregaReal,
        List<EventoEnvioResponse> eventos) {
}
