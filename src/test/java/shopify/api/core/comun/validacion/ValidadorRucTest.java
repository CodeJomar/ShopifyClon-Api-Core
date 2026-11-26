package shopify.api.core.comun.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RUC (R-030): formato, prefijo y dígito verificador de módulo 11, con RUC reales publicados por SUNAT. */
class ValidadorRucTest {

    @Test
    void aceptaRucReales() {
        assertThat(ValidadorRuc.esValido("20100070970")).isTrue(); // verificador 0 (resto 10)
        assertThat(ValidadorRuc.esValido("20131312955")).isTrue(); // verificador 5
        assertThat(ValidadorRuc.esValido("20600000013")).isTrue(); // de demostración (206000 + 0001)
    }

    @Test
    void rechazaUnDigitoVerificadorEquivocado() {
        assertThat(ValidadorRuc.esValido("20100070971")).isFalse();
        assertThat(ValidadorRuc.esValido("20131312950")).isFalse();
    }

    @Test
    void rechazaPrefijosQueNoSon10_15_17_20() {
        for (String prefijo : new String[]{"11", "12", "18", "21", "30", "00", "99"}) {
            assertThat(ValidadorRuc.esValido(prefijo + "100070970")).as("prefijo " + prefijo).isFalse();
        }
        for (String prefijo : new String[]{"10", "15", "17", "20"}) {
            String base = prefijo + "10007097";
            assertThat(ValidadorRuc.esValido(base + ValidadorRuc.digitoVerificador(base))).as("prefijo " + prefijo).isTrue();
        }
    }

    @Test
    void rechazaLargoYCaracteresIncorrectos() {
        assertThat(ValidadorRuc.esValido("2010007097")).isFalse();    // 10 dígitos
        assertThat(ValidadorRuc.esValido("201000709700")).isFalse();  // 12 dígitos
        assertThat(ValidadorRuc.esValido("2010007097A")).isFalse();
        assertThat(ValidadorRuc.esValido("RUC12345678")).isFalse();
        assertThat(ValidadorRuc.esValido("")).isFalse();
        assertThat(ValidadorRuc.esValido(null)).isFalse();
        assertThat(ValidadorRuc.esValido("2010007097.")).isFalse();
    }

    @Test
    void normalizaQuitandoEspaciosYGuiones() {
        assertThat(ValidadorRuc.normalizar(" 20 1000-70970 ")).isEqualTo("20100070970");
        assertThat(ValidadorRuc.normalizar("20-10007097-0")).isEqualTo("20100070970");
        assertThat(ValidadorRuc.normalizar("   ")).isNull();
        assertThat(ValidadorRuc.normalizar(null)).isNull();
        assertThat(ValidadorRuc.esValido("20 1000-70970")).isTrue();
    }

    @Test
    void elDigitoVerificadorUsaLosPesosYLaReglaDelCero() {
        assertThat(ValidadorRuc.digitoVerificador("2010007097")).isZero();   // 11 - 1 = 10 -> 0
        assertThat(ValidadorRuc.digitoVerificador("2013131295")).isEqualTo(5);
        // 11 - 0 = 11 -> 1: suma múltiplo de 11 (2060000008 -> 2*5+6*3+8*... se busca por fuerza bruta)
        boolean vioElUno = false;
        for (int i = 0; i < 2000 && !vioElUno; i++) {
            vioElUno = ValidadorRuc.digitoVerificador("20" + String.format("%08d", i)) == 1;
        }
        assertThat(vioElUno).isTrue();
    }
}
