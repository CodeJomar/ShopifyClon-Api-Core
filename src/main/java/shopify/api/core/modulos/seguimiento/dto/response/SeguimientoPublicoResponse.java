package shopify.api.core.modulos.seguimiento.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import shopify.api.core.modulos.envio.modelo.EstadoEnvio;

/** Seguimiento público de un envío por número de guía. No incluye datos personales ni de la tienda. */
public record SeguimientoPublicoResponse(
        String numeroGuia,
        EstadoEnvio estado,
        String empresaTransporte,
        String chofer,
        Instant fechaDespacho,
        Instant fechaEntregaEstimada,
        Instant fechaEntregaReal,
        List<Evento> eventos) {

    public record Evento(UUID id, String estado, String nota, String fotoUrl, Instant fechaEvento) {
    }
}
