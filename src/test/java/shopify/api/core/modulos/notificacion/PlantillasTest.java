package shopify.api.core.modulos.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.notificacion.plantilla.PlantillaCorreo;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas;
import shopify.api.core.modulos.notificacion.plantilla.Plantillas.Mensaje;

/** Los correos llevan la marca OTemu, nunca la antigua, y todo texto de personas se escapa en el HTML. */
class PlantillasTest {

    private static List<Mensaje> todas(String nombre) {
        BigDecimal total = new BigDecimal("25.50");
        return List.of(
                Plantillas.activacion(nombre, "123456", "https://otemu.test/activar-cuenta?correo=a%40b.test"),
                Plantillas.recuperacion(nombre, "123456"),
                Plantillas.cuentaBloqueada(nombre, "123456", 15),
                Plantillas.segundoFactor(nombre, "123456"),
                Plantillas.ordenCreadaComprador("#1001", nombre, total, "PEN", "https://otemu.test/t/ordenes/abc"),
                Plantillas.ordenCreadaTienda("#1001", "Mi tienda", total, "PEN"),
                Plantillas.ordenPagada("#1001", nombre),
                Plantillas.ordenCancelada("#1001", nombre, "CLIENTE"),
                Plantillas.envioDespachado("#1001", nombre, "DHL", "G-1", "https://seguimiento.test/1"),
                Plantillas.envioEntregado("#1001", nombre),
                Plantillas.suscripcionVencida("Mi tienda", "https://otemu.test/panel/suscripcion"),
                Plantillas.suscripcionRenovada("Mi tienda"));
    }

    @Test
    void todosLosCorreosTienenHtmlConLaMarcaOTemuYNingunoDiceShopify() {
        for (Mensaje m : todas("Ana")) {
            assertThat(m.html()).contains("OTemu").startsWith("<!DOCTYPE html>").endsWith("</html>");
            assertThat(m.asunto() + m.texto() + m.html()).doesNotContainIgnoringCase("shopify");
        }
    }

    @Test
    void elTextoDeUnaPersonaSeEscapaEnElHtmlPeroNoEnElTextoPlano() {
        String malicioso = "<script>alert(1)</script>";
        for (Mensaje m : todas(malicioso)) {
            assertThat(m.html()).doesNotContain("<script>");
        }
        Mensaje m = Plantillas.ordenPagada("#1", malicioso);
        assertThat(m.html()).contains("&lt;script&gt;");
        assertThat(m.texto()).contains(malicioso);
    }

    @Test
    void elBotonSoloAceptaEnlacesHttp() {
        String peligroso = PlantillaCorreo.nuevo("x", "t").boton("Abrir", "javascript:alert(1)").html();
        assertThat(peligroso).doesNotContain("javascript:").doesNotContain("Abrir");
        String seguro = PlantillaCorreo.nuevo("x", "t").boton("Abrir", "https://otemu.test/a?b=1&c=2").html();
        assertThat(seguro).contains("href=\"https://otemu.test/a?b=1&amp;c=2\"");
    }

    @Test
    void elCodigoApareceEnElHtmlYElAsuntoDeActivacionDiceOTemu() {
        Mensaje m = Plantillas.activacion("Ana", "482913", "https://otemu.test/activar-cuenta?correo=a%40b.test");
        assertThat(m.asunto()).isEqualTo("Activa tu cuenta en OTemu");
        assertThat(m.html()).contains("482913").contains("24 horas");
    }
}
