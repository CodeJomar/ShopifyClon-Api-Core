package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.modulos.tienda.modelo.ValidadorSubdominio;

class ValidadorSubdominioTest {

    @Test
    void normalizaEspaciosYMayusculas() {
        assertThat(ValidadorSubdominio.validar("  MI-Tienda-2 ")).isEqualTo("mi-tienda-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a-b", "mi-tienda-2", "123", "a1b2c3"})
    void aceptaSubdominiosValidos(String subdominio) {
        assertThat(ValidadorSubdominio.validar(subdominio)).isEqualTo(subdominio);
    }

    @Test
    void aceptaElLargoMaximoYRechazaElSiguiente() {
        assertThat(ValidadorSubdominio.validar("a".repeat(63))).hasSize(63);
        assertThatThrownBy(() -> ValidadorSubdominio.validar("a".repeat(64)))
                .hasMessage("El subdominio debe tener entre 3 y 63 caracteres");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ab|El subdominio debe tener entre 3 y 63 caracteres",
            "|El subdominio debe tener entre 3 y 63 caracteres",
            "-tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "tienda-|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "mi--tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "mi tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "mi_tienda|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "ñandú|Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion",
            "admin|Ese subdominio está reservado",
            "WWW|Ese subdominio está reservado",
            "plataforma|Ese subdominio está reservado"})
    void rechazaConUnMensajePorCaso(String subdominio, String mensaje) {
        assertThatThrownBy(() -> ValidadorSubdominio.validar(subdominio))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage(mensaje)
                .extracting("codigo").isEqualTo("TIE-001");
    }

    @Test
    void nuloEsMuyCorto() {
        assertThatThrownBy(() -> ValidadorSubdominio.validar(null))
                .hasMessage("El subdominio debe tener entre 3 y 63 caracteres");
    }

    @Test
    void elMensajeDeUsoTieneSuCodigo() {
        assertThat(ValidadorSubdominio.enUso()).hasMessage("Ese subdominio ya está en uso")
                .extracting("codigo").isEqualTo("TIE-001");
    }
}
