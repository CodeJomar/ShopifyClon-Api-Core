package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** Proveedor S3 (Bucket de Railway): clave, tipo, URL pública, lectura y errores. */
class S3AlmacenamientoTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    void subeConClaveUnicaTipoYCacheYDevuelveLaUrlDeLaApi() {
        S3Client s3 = mock(S3Client.class);
        S3Almacenamiento almacenamiento = new S3Almacenamiento(s3, "imagenes", "https://api.ejemplo.test/api/v1/");
        UUID tienda = UUID.randomUUID();

        String url = almacenamiento.subir(tienda, JPEG, "image/jpeg");

        ArgumentCaptor<PutObjectRequest> peticion = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(peticion.capture(), any(RequestBody.class));
        assertThat(peticion.getValue().bucket()).isEqualTo("imagenes");
        assertThat(peticion.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(peticion.getValue().cacheControl()).contains("immutable");
        assertThat(peticion.getValue().key()).startsWith(tienda + "/").endsWith(".jpg");
        assertThat(url).isEqualTo("https://api.ejemplo.test/api/v1/archivos/" + peticion.getValue().key());
    }

    @Test
    void unFalloDelBucketSeConvierteEnUnErrorDeNegocioSinFiltrarDetalles() {
        S3Client s3 = mock(S3Client.class);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(S3Exception.builder().message("secreto interno").build());
        S3Almacenamiento almacenamiento = new S3Almacenamiento(s3, "imagenes", "https://api.ejemplo.test/api/v1");

        assertThatThrownBy(() -> almacenamiento.subir(UUID.randomUUID(), JPEG, "image/png"))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getCodigo()).isEqualTo("ALM-002"))
                .hasMessageNotContaining("secreto interno");
    }

    @Test
    void sinConfiguracionResponde503ConCodigoPropio() {
        S3Almacenamiento almacenamiento = new S3Almacenamiento("", "auto", "", "", "", false, "https://api.ejemplo.test/api/v1");

        assertThatThrownBy(() -> almacenamiento.subir(UUID.randomUUID(), JPEG, "image/jpeg"))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .satisfies(ex -> assertThat(((AlmacenamientoNoConfiguradoException) ex).getCodigo()).isEqualTo("ALM-001"));
        assertThat(almacenamiento.leer("a/b.jpg")).isEmpty();
    }

    @Test
    void rechazaTiposQueNoSonImagenSoportada() {
        S3Almacenamiento almacenamiento = new S3Almacenamiento(mock(S3Client.class), "imagenes", "https://api.ejemplo.test/api/v1");

        assertThatThrownBy(() -> almacenamiento.subir(UUID.randomUUID(), JPEG, "application/pdf"))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void leeUnArchivoYDevuelveVacioSiNoExiste() {
        S3Client s3 = mock(S3Client.class);
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().contentType("image/webp").build(), JPEG))
                .thenThrow(NoSuchKeyException.builder().message("no existe").build());
        S3Almacenamiento almacenamiento = new S3Almacenamiento(s3, "imagenes", "https://api.ejemplo.test/api/v1");

        var archivo = almacenamiento.leer("t/a.webp");
        assertThat(archivo).isPresent();
        assertThat(archivo.get().tipo()).isEqualTo("image/webp");
        assertThat(archivo.get().contenido()).isEqualTo(JPEG);
        assertThat(almacenamiento.leer("t/otro.webp")).isEmpty();
    }
}
