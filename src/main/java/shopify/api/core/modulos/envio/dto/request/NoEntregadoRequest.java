package shopify.api.core.modulos.envio.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Intento de entrega fallido: la nota con el motivo es obligatoria. */
public record NoEntregadoRequest(
        @NotBlank(message = "Indica el motivo por el que no se entregó")
        @Size(max = 500, message = "La nota no puede superar los 500 caracteres") String nota) {
}
