package shopify.api.core.comun.almacenamiento;

import java.util.Optional;
import java.util.UUID;

/**
 * Interfaz de almacenamiento de imágenes. Permite intercambiar el proveedor
 * (Supabase Storage, S3, R2, disco local) sin tocar el servicio que la usa.
 * <p>
 * La implementación activa se elige con {@code app.almacenamiento.proveedor}
 * ({@code s3} para un bucket compatible con S3, como el de Railway; {@code supabase} por defecto).
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

    /**
     * {@code true} si la URL es de una imagen que este almacén guardó para esa tienda (la que devuelve
     * {@link #subir}). Sirve para aceptar solo imágenes subidas por la tienda (por ejemplo su logo), nunca URL ajenas.
     */
    default boolean esUrlPropia(UUID tiendaId, String url) {
        return false;
    }

    /** Archivo guardado y su tipo MIME. */
    record ArchivoAlmacenado(byte[] contenido, String tipo) {
    }

    /**
     * Lee un archivo guardado ({@code <tiendaId>/<archivo>}) para servirlo desde la API. Solo lo necesitan los
     * proveedores con bucket privado (S3/Railway); Supabase entrega sus propias URL públicas y devuelve vacío.
     */
    default Optional<ArchivoAlmacenado> leer(String ruta) {
        return Optional.empty();
    }
}
