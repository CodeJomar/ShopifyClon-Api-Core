package shopify.api.core.modulos.carrito.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Tarifa de envío elegida para cada tienda del carrito; una tienda sin entrada se cotiza sin envío. */
public record CotizarCarritoRequest(
        @NotNull(message = "Indica los envíos")
        @Size(max = 50, message = "Demasiadas tiendas")
        List<@Valid EnvioElegido> envios) {

    public record EnvioElegido(
            @NotNull(message = "Indica la tienda") UUID tiendaId,
            @NotNull(message = "Indica la tarifa de envío") UUID tarifaEnvioId) {
    }
}
