package shopify.api.core.modulos.envio.dto.request;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional al registrar la entrega: foto (https, subida con el almacenamiento de imágenes) y nota. */
public record EntregaRequest(
        @Size(max = 2000, message = "La URL de la foto no puede superar los 2000 caracteres") String fotoUrl,
        @Size(max = 500, message = "La nota no puede superar los 500 caracteres") String nota) {
}
