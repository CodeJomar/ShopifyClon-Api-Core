package shopify.api.core.modulos.carrito.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Cupón del carrito global: el código solo aplica a las líneas de la tienda indicada. */
public record CuponGlobalRequest(
        @NotNull(message = "Indica la tienda") UUID tiendaId,

        @NotBlank(message = "Indica el código del cupón")
        @Size(max = 50, message = "El código no puede superar los 50 caracteres")
        String codigo) {
}
