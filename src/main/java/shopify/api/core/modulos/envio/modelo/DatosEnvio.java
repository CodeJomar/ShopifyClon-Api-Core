package shopify.api.core.modulos.envio.modelo;

import java.util.UUID;

/** Datos del envío para el correo de despacho y entrega. */
public record DatosEnvio(UUID ordenId, String transportista, String numeroGuia, String urlSeguimiento) {
}
