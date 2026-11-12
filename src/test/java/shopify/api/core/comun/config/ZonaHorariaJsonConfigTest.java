package shopify.api.core.comun.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** Las fechas con hora salen con el desfase de Lima (-05:00) y siguen siendo el mismo instante. */
class ZonaHorariaJsonConfigTest extends PruebaIntegracionBase {

    @Test
    void laMarcaDeTiempoViajaConElDesfaseDeLima() throws Exception {
        String marca = cuerpo(mvc.perform(get("/health")).andExpect(status().isOk())).get("marca_tiempo").asText();

        assertThat(marca).endsWith("-05:00");
        OffsetDateTime fecha = OffsetDateTime.parse(marca);
        assertThat(fecha.toInstant()).isBetween(java.time.Instant.now().minusSeconds(60), java.time.Instant.now().plusSeconds(5));
    }
}
