package shopify.api.core.comun.almacenamiento;

import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.Permiso;

/**
 * Servicio de subida de imágenes. Valida tienda, permisos, tipo (por magic bytes)
 * y tamaño, luego delega en {@link AlmacenamientoImagenes} (interfaz intercambiable).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubidaImagenServicio {

    private static final String CODIGO_TIPO_NO_PERMITIDO = "ALM-004";
    private static final String CODIGO_TAMANO_EXCEDIDO = "ALM-005";

    /** 5 MB en bytes. */
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;

    // Magic bytes para detección de tipo
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] WEBP_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF"
    private static final byte[] WEBP_MARKER = {0x57, 0x45, 0x42, 0x50}; // "WEBP"

    private final AlmacenamientoImagenes almacenamiento;

    /**
     * Valida tienda, permisos, tamaño, tipo y sube la imagen al almacenamiento configurado.
     *
     * @param contexto contexto de la tienda (ya validado por el interceptor)
     * @param archivo  archivo multipart recibido
     * @return respuesta con la URL pública https
     */
    public ImagenSubidaResponse subir(ContextoTienda contexto, MultipartFile archivo) {
        if (contexto == null || contexto.tiendaId() == null) {
            throw new SolicitudInvalidaException("SEG-001", "Debes indicar la tienda en la que trabajas");
        }
        contexto.exigir(Permiso.CATALOGO_GESTIONAR);

        validarTamano(archivo);
        byte[] contenido = leerContenido(archivo);
        String tipoDetectado = detectarTipoPorBytes(contenido);
        String url = almacenamiento.subir(contexto.tiendaId(), contenido, tipoDetectado);
        log.info("Imagen subida para tienda {}: {}", contexto.tiendaId(), url);
        return new ImagenSubidaResponse(url);
    }

    private void validarTamano(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReglaNegocioException(CODIGO_TIPO_NO_PERMITIDO, "Debes seleccionar un archivo de imagen");
        }
        if (archivo.getSize() > TAMANO_MAXIMO) {
            throw new ReglaNegocioException(CODIGO_TAMANO_EXCEDIDO,
                    "La imagen no puede superar los 5 MB");
        }
    }

    private byte[] leerContenido(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (Exception e) {
            throw new ReglaNegocioException(CODIGO_TIPO_NO_PERMITIDO, "No se pudo leer el archivo");
        }
    }

    /**
     * Detecta el tipo MIME analizando los bytes iniciales (magic bytes).
     * No confía en el Content-Type enviado por el cliente.
     */
    String detectarTipoPorBytes(byte[] contenido) {
        if (contenido == null || contenido.length < 12) {
            throw new ReglaNegocioException(CODIGO_TIPO_NO_PERMITIDO,
                    "El archivo no es una imagen válida (JPEG, PNG o WebP)");
        }

        if (comienzaCon(contenido, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (comienzaCon(contenido, PNG_MAGIC)) {
            return "image/png";
        }
        if (comienzaCon(contenido, WEBP_RIFF) && regionIgual(contenido, 8, WEBP_MARKER)) {
            return "image/webp";
        }

        throw new ReglaNegocioException(CODIGO_TIPO_NO_PERMITIDO,
                "El archivo no es una imagen válida (JPEG, PNG o WebP)");
    }

    private static boolean comienzaCon(byte[] datos, byte[] prefijo) {
        if (datos.length < prefijo.length) return false;
        return Arrays.equals(datos, 0, prefijo.length, prefijo, 0, prefijo.length);
    }

    private static boolean regionIgual(byte[] datos, int offset, byte[] esperado) {
        if (datos.length < offset + esperado.length) return false;
        return Arrays.equals(datos, offset, offset + esperado.length, esperado, 0, esperado.length);
    }
}
