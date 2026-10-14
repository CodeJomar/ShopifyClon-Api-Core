package shopify.api.core.comun.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeneradorTokenTest {

    private final GeneradorToken generador = new GeneradorToken();

    @Test
    void tokenUrlTiene43CaracteresBase64UrlSinRelleno() {
        assertThat(generador.tokenUrl()).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void tokensSonUnicosEn1000Llamadas() {
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            vistos.add(generador.tokenUrl());
        }
        assertThat(vistos).hasSize(1000);
    }

    @Test
    void codigoNumericoTieneLosDigitosPedidos() {
        for (int i = 0; i < 200; i++) {
            assertThat(generador.codigoNumerico(6)).hasSize(6).matches("\\d{6}");
        }
    }

    @Test
    void sha256HexCoincideConElVectorConocido() {
        assertThat(GeneradorToken.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
