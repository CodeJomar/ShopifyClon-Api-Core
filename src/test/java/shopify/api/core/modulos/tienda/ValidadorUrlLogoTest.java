package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.tienda.modelo.ValidadorUrlLogo;

/** R-031: el logo es una URL https válida, propia o externa; nada que pueda ejecutar código ni que filtre credenciales. */
class ValidadorUrlLogoTest {

    @Test
    void aceptaUrlHttpsConServidor() {
        for (String buena : List.of("https://cdn.ejemplo.test/logo.png", "https://cdn.ejemplo.test/mi-logo.png?v=2&x=1#a",
                "https://sub.dominio.ejemplo.test:8443/a/b/c.webp", "https://xn--espaa-rta.test/l.jpg",
                "https://api.ejemplo.test/api/v1/archivos/3f2b8c1e-0a4d-4f6e-9b7a-1c2d3e4f5a6b/0b9f6d2e-5c3a-4e8f-a1b2-c3d4e5f60718.png",
                "https://cdn.ejemplo.test/" + "a".repeat(500 - "https://cdn.ejemplo.test/".length()))) {
            assertThat(ValidadorUrlLogo.esValida(buena)).as(buena).isTrue();
        }
    }

    @Test
    void rechazaEsquemasPeligrososHttpYUrlMalFormadas() {
        for (String mala : List.of("http://cdn.ejemplo.test/logo.png", "javascript:alert(1)", "JAVASCRIPT:alert(1)",
                "data:image/png;base64,AAAA", "ftp://cdn.ejemplo.test/logo.png", "file:///etc/passwd", "//cdn.ejemplo.test/logo.png",
                "https://", "https:///logo.png", "https:logo.png", "logo.png", "", "   ", "https://usuario:clave@cdn.ejemplo.test/l.png",
                "https://usuario@cdn.ejemplo.test/l.png")) {
            assertThat(ValidadorUrlLogo.esValida(mala)).as(mala).isFalse();
        }
        assertThat(ValidadorUrlLogo.esValida(null)).isFalse();
    }

    @Test
    void rechazaEspaciosControlComillasYSignosPeligrosos() {
        for (String mala : List.of("https://cdn.ejemplo.test/mi logo.png", " https://cdn.ejemplo.test/l.png", "https://cdn.ejemplo.test/l.png ",
                "https://cdn.ejemplo.test/l.png\n", "https://cdn.ejemplo.test/\tl.png", "https://cdn.ejemplo.test/\u0000l.png",
                "https://cdn.ejemplo.test/\"onerror=alert(1)", "https://cdn.ejemplo.test/'x", "https://cdn.ejemplo.test/<script>",
                "https://cdn.ejemplo.test/a>b", "https://cdn.ejemplo.test/a\\b", "https://cdn.ejemplo.test/ l.png")) {
            assertThat(ValidadorUrlLogo.esValida(mala)).as(mala).isFalse();
        }
    }

    @Test
    void rechazaMasDe500Caracteres() {
        assertThat(ValidadorUrlLogo.esValida("https://cdn.ejemplo.test/" + "a".repeat(501 - "https://cdn.ejemplo.test/".length()))).isFalse();
        assertThat(ValidadorUrlLogo.esValida("https://cdn.ejemplo.test/" + "a".repeat(500))).isFalse();
    }
}
