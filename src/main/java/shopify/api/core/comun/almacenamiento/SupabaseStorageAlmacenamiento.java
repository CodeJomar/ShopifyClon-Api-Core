package shopify.api.core.comun.almacenamiento;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import shopify.api.core.comun.excepcion.ReglaNegocioException;

/**
 * Implementación de {@link AlmacenamientoImagenes} contra Supabase Storage REST API.
 * <p>
 * Se activa cuando {@code app.almacenamiento.proveedor=supabase} (por defecto).
 * Requiere {@code app.supabase.url} y {@code app.supabase.secret-key}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.almacenamiento.proveedor", havingValue = "supabase", matchIfMissing = true)
public class SupabaseStorageAlmacenamiento implements AlmacenamientoImagenes {

    private static final String CODIGO_ALMACENAMIENTO_NO_CONFIGURADO = "ALM-001";
    private static final String CODIGO_ALMACENAMIENTO_ERROR = "ALM-002";

    private final String supabaseUrl;
    private final String secretKey;
    private final String bucket;
    private final RestClient restClient;

    public SupabaseStorageAlmacenamiento(
            @Value("${app.supabase.url:}") String supabaseUrl,
            @Value("${app.supabase.secret-key:}") String secretKey,
            @Value("${app.supabase.bucket-imagenes:ShopifyClon-Images-Core}") String bucket) {
        this.supabaseUrl = supabaseUrl;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.restClient = RestClient.create();
    }

    @Override
    public String subir(UUID tiendaId, byte[] contenido, String tipo) {
        validarConfiguracion();

        String extension = extensionDe(tipo);
        String nombreArchivo = UUID.randomUUID() + "." + extension;
        String rutaObjeto = tiendaId + "/" + nombreArchivo;

        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + rutaObjeto;

        try {
            restClient.post()
                    .uri(uploadUrl)
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(tipo))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000")
                    .body(contenido)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        byte[] body = response.getBody().readAllBytes();
                        String respuesta = new String(body);
                        log.error("Error al subir imagen a Supabase Storage [{}]: {}", response.getStatusCode(), respuesta);
                        throw new ReglaNegocioException(CODIGO_ALMACENAMIENTO_ERROR,
                                "No se pudo guardar la imagen. Intenta de nuevo más tarde");
                    })
                    .toBodilessEntity();
        } catch (ReglaNegocioException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error de conexión con Supabase Storage", e);
            throw new ReglaNegocioException(CODIGO_ALMACENAMIENTO_ERROR,
                    "No se pudo guardar la imagen. Intenta de nuevo más tarde");
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + rutaObjeto;
    }

    /**
     * Verifica que el bucket existe y es accesible. Para uso externo (verificación manual).
     *
     * @return {@code true} si el bucket responde correctamente
     */
    public boolean bucketExiste() {
        if (supabaseUrl == null || supabaseUrl.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        try {
            restClient.get()
                    .uri(supabaseUrl + "/storage/v1/bucket/" + bucket)
                    .header("apikey", secretKey)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + secretKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RuntimeException("Bucket no encontrado: " + response.getStatusCode());
                    })
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("No se pudo verificar el bucket '{}': {}", bucket, e.getMessage());
            return false;
        }
    }

    private void validarConfiguracion() {
        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            throw new AlmacenamientoNoConfiguradoException(CODIGO_ALMACENAMIENTO_NO_CONFIGURADO,
                    "El servicio de almacenamiento de imágenes no está configurado");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new AlmacenamientoNoConfiguradoException(CODIGO_ALMACENAMIENTO_NO_CONFIGURADO,
                    "El servicio de almacenamiento de imágenes no está configurado");
        }
    }

    private static String extensionDe(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ReglaNegocioException("ALM-003", "Tipo de imagen no soportado: " + mimeType);
        };
    }
}
