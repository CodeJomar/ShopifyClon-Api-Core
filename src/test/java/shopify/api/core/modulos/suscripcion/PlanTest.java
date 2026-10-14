package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class PlanTest extends PruebaSuscripcionBase {

    private JsonNode planesPublicos() throws Exception {
        return cuerpo(getConToken(Rutas.PLANES, null, null).andExpect(status().isOk())).get("datos");
    }

    @Test
    void listaLosTresPlanesEnOrdenSinDatosInternos() throws Exception {
        JsonNode planes = planesPublicos();

        assertThat(planes).hasSize(3);
        assertThat(planes.get(0).get("codigo").asText()).isEqualTo("BASICO");
        assertThat(planes.get(1).get("codigo").asText()).isEqualTo("ESTANDAR");
        assertThat(planes.get(2).get("codigo").asText()).isEqualTo("AVANZADO");

        // AVANZADO es ilimitado: el nulo se omite del JSON
        assertThat(planes.get(2).has("limite_productos")).isFalse();
        assertThat(new BigDecimal(planes.get(0).get("porcentaje_comision").asText())).isEqualByComparingTo("0.02");

        for (JsonNode plan : planes) {
            assertThat(plan.has("id")).isFalse();
            assertThat(plan.has("es_publico")).isFalse();
            assertThat(plan.has("orden")).isFalse();
        }
    }

    @Test
    void noListaPlanesNoPublicosNiEliminados() throws Exception {
        String noPublico = codigoPlanUnico();
        String eliminado = codigoPlanUnico();
        insertarPlan(noPublico, false, false, 14);
        insertarPlan(eliminado, true, true, 14);

        List<String> codigos = new ArrayList<>();
        planesPublicos().forEach(plan -> codigos.add(plan.get("codigo").asText()));

        assertThat(codigos).containsExactly("BASICO", "ESTANDAR", "AVANZADO");
    }
}
