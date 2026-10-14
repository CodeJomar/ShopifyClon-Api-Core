package shopify.api.core.modulos.envio.dto.request;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Datos editables de un envío. Crear y actualizar usan el mismo cuerpo; la URL debe ser https (ENV-006). */
public record EnvioRequest(
        @Size(max = 100) String empresaTransporte,
        @Size(max = 100) String numeroGuia,
        @Size(max = 2000) String urlSeguimiento,
        Instant fechaEntregaEstimada) {
}
