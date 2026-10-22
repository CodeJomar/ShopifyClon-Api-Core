package shopify.api.core.comun.almacenamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import shopify.api.core.comun.excepcion.ReglaNegocioException;

/**
 * Pruebas unitarias de la detección de tipo por magic bytes
 * en {@link SubidaImagenServicio}.
 */
class DeteccionTipoImagenTest {

    private final SubidaImagenServicio servicio =
            new SubidaImagenServicio((_tiendaId, _contenido, _tipo) -> "https://fake.test/img.jpg");

    @Test
    void detectaJpeg() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(servicio.detectarTipoPorBytes(jpeg)).isEqualTo("image/jpeg");
    }

    @Test
    void detectaPng() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(servicio.detectarTipoPorBytes(png)).isEqualTo("image/png");
    }

    @Test
    void detectaWebp() {
        // RIFF + 4 bytes de tamaño + WEBP
        byte[] webp = {0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50};
        assertThat(servicio.detectarTipoPorBytes(webp)).isEqualTo("image/webp");
    }

    @Test
    void rechazaGif() {
        byte[] gif = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0};
        assertThatThrownBy(() -> servicio.detectarTipoPorBytes(gif))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es una imagen válida");
    }

    @Test
    void rechazaTextoPlano() {
        byte[] texto = "Esto es texto plano, no imagen".getBytes();
        assertThatThrownBy(() -> servicio.detectarTipoPorBytes(texto))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es una imagen válida");
    }

    @Test
    void rechazaArchivoMuyCorto() {
        byte[] corto = {0x01, 0x02};
        assertThatThrownBy(() -> servicio.detectarTipoPorBytes(corto))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es una imagen válida");
    }

    @Test
    void rechazaNull() {
        assertThatThrownBy(() -> servicio.detectarTipoPorBytes(null))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void pngRenombradoAExeSeAceptaSiBytesSonPng() {
        // Simula un archivo con extensión .exe pero bytes de PNG
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(servicio.detectarTipoPorBytes(png)).isEqualTo("image/png");
    }

    @Test
    void textoRenombradoAPngSeRechaza() {
        // Un archivo de texto renombrado a .png: los bytes no son PNG
        byte[] texto = "not a png file at all".getBytes();
        assertThatThrownBy(() -> servicio.detectarTipoPorBytes(texto))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("no es una imagen válida");
    }
}
