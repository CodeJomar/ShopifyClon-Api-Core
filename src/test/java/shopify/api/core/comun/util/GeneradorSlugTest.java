package shopify.api.core.comun.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeneradorSlugTest {

    @Test
    void quitaTildesEnieEspaciosYSimbolos() {
        assertThat(GeneradorSlug.generar("Camiseta Ñandú  Edición #1", 100)).isEqualTo("camiseta-nandu-edicion-1");
    }

    @Test
    void textoSoloDeSimbolosDevuelveItem() {
        assertThat(GeneradorSlug.generar("¡¿ ## !!", 50)).isEqualTo("item");
        assertThat(GeneradorSlug.generar("", 50)).isEqualTo("item");
        assertThat(GeneradorSlug.generar(null, 50)).isEqualTo("item");
    }

    @Test
    void recortaSinTerminarEnGuion() {
        assertThat(GeneradorSlug.generar("abcd efgh", 5)).isEqualTo("abcd");
        assertThat(GeneradorSlug.generar("abcdefgh", 3)).isEqualTo("abc");
    }

    @Test
    void colapsaGuionesYQuitaLosDeLosExtremos() {
        assertThat(GeneradorSlug.generar("--a---b--", 50)).isEqualTo("a-b");
    }
}
