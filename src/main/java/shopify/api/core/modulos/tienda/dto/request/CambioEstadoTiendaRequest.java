package shopify.api.core.modulos.tienda.dto.request;

import jakarta.validation.constraints.Size;

/** Motivo opcional al activar o desactivar la tienda; queda en la auditoría. */
public record CambioEstadoTiendaRequest(
        @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
        String motivo) {
}
