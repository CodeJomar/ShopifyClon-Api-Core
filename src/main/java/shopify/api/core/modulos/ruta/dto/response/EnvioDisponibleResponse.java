package shopify.api.core.modulos.ruta.dto.response;

import java.util.UUID;

/** Envío que se puede agregar a una ruta (creado o en tránsito, sin ruta activa). */
public record EnvioDisponibleResponse(
        UUID envioId,
        String numeroOrden,
        String numeroGuia,
        String estadoEnvio,
        String destinatario,
        String ciudad,
        String direccionLinea1,
        long totalItems) {
}
