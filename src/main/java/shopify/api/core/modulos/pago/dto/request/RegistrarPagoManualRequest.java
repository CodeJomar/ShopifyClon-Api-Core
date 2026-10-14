package shopify.api.core.modulos.pago.dto.request;

import jakarta.validation.constraints.Size;

/** Pago manual: la referencia (número de operación) es opcional. */
public record RegistrarPagoManualRequest(@Size(max = 100) String referencia) {
}
