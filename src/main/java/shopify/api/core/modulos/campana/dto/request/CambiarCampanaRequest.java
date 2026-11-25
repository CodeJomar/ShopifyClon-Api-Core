package shopify.api.core.modulos.campana.dto.request;

/** {@code codigo} nulo (o vacío) apaga la campaña vigente. */
public record CambiarCampanaRequest(String codigo) {
}
