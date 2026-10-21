package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Pruebas unitarias de {@link SupabaseStorageAlmacenamiento}:
 * validación de configuración, códigos de error y URLs públicas.
 */
class SupabaseStorageAlmacenamientoTest {

    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    @Test
    void sinClaveSecretaLanza503ConCodigoPropio() {
        SupabaseStorageAlmacenamiento almacenamiento =
                new SupabaseStorageAlmacenamiento("https://ejemplo.supabase.co", "", "ShopifyClon-Images-Core");

        assertThatThrownBy(() -> almacenamiento.subir(UUID.randomUUID(), FAKE_JPEG, "image/jpeg"))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .satisfies(ex -> {
                    AlmacenamientoNoConfiguradoException e = (AlmacenamientoNoConfiguradoException) ex;
                    assertThat(e.getCodigo()).isEqualTo("ALM-001");
                    assertThat(e.getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void sinUrlSupabaseLanza503ConCodigoPropio() {
        SupabaseStorageAlmacenamiento almacenamiento =
                new SupabaseStorageAlmacenamiento("", "sb_secret_test", "ShopifyClon-Images-Core");

        assertThatThrownBy(() -> almacenamiento.subir(UUID.randomUUID(), FAKE_JPEG, "image/jpeg"))
                .isInstanceOf(AlmacenamientoNoConfiguradoException.class)
                .satisfies(ex -> {
                    AlmacenamientoNoConfiguradoException e = (AlmacenamientoNoConfiguradoException) ex;
                    assertThat(e.getCodigo()).isEqualTo("ALM-001");
                    assertThat(e.getEstado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }

    @Test
    void bucketExisteDevuelveFalseSinConfiguracion() {
        SupabaseStorageAlmacenamiento almacenamiento =
                new SupabaseStorageAlmacenamiento("", "", "ShopifyClon-Images-Core");
        assertThat(almacenamiento.bucketExiste()).isFalse();
    }
}
