package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Sube y lee de verdad contra un servidor S3 (S3Mock): valida el endpoint propio, la firma de las peticiones y el acceso por ruta, que es
 * lo que usa el Bucket de Railway. Sin mocks.
 */
class S3AlmacenamientoServidorTest {

    private static final String USUARIO = "prueba-usuario";
    private static final String CLAVE = "prueba-clave-123456";
    private static final String BUCKET = "imagenes-prueba";
    private static GenericContainer<?> minio;

    @BeforeAll
    static void levantar() {
        minio = new GenericContainer<>("adobe/s3mock:3.11.0")
                .withEnv("initialBuckets", BUCKET)
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCodeMatching(c -> c < 500));
        minio.start();
    }

    @AfterAll
    static void apagar() {
        if (minio != null) {
            minio.stop();
        }
    }

    private String endpoint() {
        return "http://" + minio.getHost() + ":" + minio.getMappedPort(9090);
    }

    @Test
    void subeYLeeUnaImagenDeVerdad() {
        S3Almacenamiento almacenamiento = new S3Almacenamiento(endpoint(), "us-east-1", BUCKET, USUARIO, CLAVE, true,
                "https://api.ejemplo.test/api/v1");
        almacenamiento.comprobarBucket();
        UUID tienda = UUID.randomUUID();
        byte[] contenido = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4, 5};

        String url = almacenamiento.subir(tienda, contenido, "image/png");

        String ruta = url.substring("https://api.ejemplo.test/api/v1/archivos/".length());
        assertThat(ruta).startsWith(tienda + "/").endsWith(".png");
        var leido = almacenamiento.leer(ruta);
        assertThat(leido).isPresent();
        assertThat(leido.get().contenido()).isEqualTo(contenido);
        assertThat(leido.get().tipo()).isEqualTo("image/png");
        assertThat(almacenamiento.leer(tienda + "/" + UUID.randomUUID() + ".png")).isEmpty();
    }
}
