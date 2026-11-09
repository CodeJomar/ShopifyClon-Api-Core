package shopify.api.core.modulos.ruta.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Crear o editar una ruta. El orden de {@code envioIds} es el orden de las paradas. */
public record RutaRequest(
        @NotNull(message = "La fecha de la ruta es obligatoria")
        LocalDate fecha,

        @NotBlank(message = "El chofer es obligatorio")
        @Size(max = 150, message = "El chofer no puede superar los 150 caracteres")
        String chofer,

        @NotBlank(message = "La placa es obligatoria")
        @Size(max = 20, message = "La placa no puede superar los 20 caracteres")
        String placa,

        @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
        String nota,

        @NotEmpty(message = "Agrega al menos un envío a la ruta")
        @Size(max = 100, message = "Una ruta admite hasta 100 envíos")
        List<@NotNull(message = "Hay un envío sin identificador") UUID> envioIds) {
}
