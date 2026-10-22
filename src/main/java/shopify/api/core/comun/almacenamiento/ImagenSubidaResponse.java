package shopify.api.core.comun.almacenamiento;

/**
 * Respuesta del endpoint de subida de imágenes.
 *
 * @param url URL pública {@code https} de la imagen subida
 */
public record ImagenSubidaResponse(String url) {
}
