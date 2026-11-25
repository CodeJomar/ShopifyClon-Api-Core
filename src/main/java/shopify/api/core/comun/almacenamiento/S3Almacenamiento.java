package shopify.api.core.comun.almacenamiento;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Almacenamiento de imágenes en un bucket compatible con S3 (por ejemplo un Bucket de Railway). Se activa con
 * {@code app.almacenamiento.proveedor=s3}. Los buckets de Railway son privados: las imágenes se sirven por la propia
 * API en {@code /archivos/<tiendaId>/<archivo>} (ver {@link ArchivoPublicoControlador}) y la URL que se guarda en la
 * base es la de la API ({@code app.s3.url-publica}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.almacenamiento.proveedor", havingValue = "s3")
public class S3Almacenamiento implements AlmacenamientoImagenes {

    private static final String CODIGO_NO_CONFIGURADO = "ALM-001";
    private static final String CODIGO_ERROR = "ALM-002";

    private final S3Client cliente;
    private final String bucket;
    private final String urlPublica;

    public S3Almacenamiento(
            @Value("${app.s3.endpoint:}") String endpoint,
            @Value("${app.s3.region:auto}") String region,
            @Value("${app.s3.bucket:}") String bucket,
            @Value("${app.s3.access-key:}") String accessKey,
            @Value("${app.s3.secret-key:}") String secretKey,
            @Value("${app.s3.path-style:false}") boolean pathStyle,
            @Value("${app.s3.url-publica:http://localhost:8080/api/v1}") String urlPublica) {
        this(construir(endpoint, region, bucket, accessKey, secretKey, pathStyle), bucket, urlPublica);
    }

    S3Almacenamiento(S3Client cliente, String bucket, String urlPublica) {
        this.cliente = cliente;
        this.bucket = bucket;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    private static S3Client construir(String endpoint, String region, String bucket, String accessKey, String secretKey,
                                      boolean pathStyle) {
        if (vacio(endpoint) || vacio(bucket) || vacio(accessKey) || vacio(secretKey)) {
            log.error("app.almacenamiento.proveedor=s3 pero faltan S3_ENDPOINT, S3_BUCKET, S3_ACCESS_KEY_ID o S3_SECRET_ACCESS_KEY: "
                    + "las imágenes no se podrán subir");
            return null;
        }
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override
    public String subir(UUID tiendaId, byte[] contenido, String tipo) {
        verificarConfiguracion();
        String clave = tiendaId + "/" + UUID.randomUUID() + "." + extensionDe(tipo);
        try {
            cliente.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(clave)
                    .contentType(tipo)
                    .cacheControl("public, max-age=31536000, immutable")
                    .build(), RequestBody.fromBytes(contenido));
        } catch (RuntimeException ex) {
            log.error("Error al subir la imagen al bucket S3: {}", ex.getClass().getSimpleName() + ": " + ex.getMessage());
            throw new ReglaNegocioException(CODIGO_ERROR, "No se pudo guardar la imagen. Intenta de nuevo más tarde");
        }
        return urlPublica + "/archivos/" + clave;
    }

    @Override
    public Optional<ArchivoAlmacenado> leer(String ruta) {
        if (cliente == null) {
            return Optional.empty();
        }
        try {
            var respuesta = cliente.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(ruta).build());
            return Optional.of(new ArchivoAlmacenado(respuesta.asByteArray(), respuesta.response().contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("No se pudo leer {} del bucket S3: {}", ruta, ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Al arrancar comprueba que el bucket responde, para enterarse en el log y no al primer usuario. */
    @EventListener(ApplicationReadyEvent.class)
    void comprobarBucket() {
        if (cliente == null) {
            return;
        }
        try {
            cliente.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("Almacenamiento S3 listo: bucket «{}» accesible", bucket);
        } catch (RuntimeException ex) {
            log.error("No se pudo acceder al bucket S3 «{}» ({}): las imágenes no se podrán subir", bucket, ex.getClass().getSimpleName());
        }
    }

    private void verificarConfiguracion() {
        if (cliente == null) {
            throw new AlmacenamientoNoConfiguradoException(CODIGO_NO_CONFIGURADO,
                    "El servicio de almacenamiento de imágenes no está configurado");
        }
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String extensionDe(String tipo) {
        return switch (tipo) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new ReglaNegocioException("ALM-003", "Tipo de imagen no soportado: " + tipo);
        };
    }
}
