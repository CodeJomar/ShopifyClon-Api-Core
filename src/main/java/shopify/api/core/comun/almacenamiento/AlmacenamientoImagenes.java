package shopify.api.core.comun.almacenamiento;

import java.util.UUID;

/**
 * Interfaz de almacenamiento de imágenes. Permite intercambiar el proveedor
 * (Supabase Storage, S3, R2, disco local) sin tocar el servicio que la usa.
 * <p>
 * La implementación activa se elige con {@code app.almacenamiento.proveedor}
 * (por defecto {@code supabase}).
 */
public interface AlmacenamientoImagenes {

    /**
     * Sube una imagen y devuelve su URL pública {@code https}.
     *
     * @param tiendaId identificador de la tienda (primer segmento de la ruta)
     * @param contenido bytes de la imagen ya validada
     * @param tipo      tipo MIME detectado por los bytes iniciales (image/jpeg, image/png, image/webp)
     * @return URL pública de la imagen subida
     * @throws shopify.api.core.comun.excepcion.ExcepcionNegocio si el proveedor falla o no está configurado
     */
    String subir(UUID tiendaId, byte[] contenido, String tipo);
}
