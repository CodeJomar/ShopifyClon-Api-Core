package shopify.api.core.modulos.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class TarifaEnvioVitrinaTest extends PruebaEnvioBase {

    @Test
    void soloMuestraTarifasActivasYNoEliminadasConCamposPublicos() throws Exception {
        Sesion propietario = nuevaSesion();
        Tienda tienda = tiendaOperativa(propietario);
        insertarTarifa(tienda.id(), "Activa", "5.00", true, 1, false);
        insertarTarifa(tienda.id(), "Inactiva", "3.00", false, 0, false);
        insertarTarifa(tienda.id(), "Eliminada", "2.00", true, 0, true);

        JsonNode datos = cuerpo(getConToken(Rutas.vitrinaTarifasEnvio(tienda.subdominio()), null, null)
                .andExpect(status().isOk())).get("datos");

        assertThat(datos).hasSize(1);
        JsonNode tarifa = datos.get(0);
        assertThat(tarifa.get("nombre").asText()).isEqualTo("Activa");
        assertThat(tarifa.has("precio")).isTrue();
        assertThat(tarifa.has("id")).isTrue();
        assertThat(tarifa.has("orden")).isFalse();
        assertThat(tarifa.has("esta_activa")).isFalse();
    }

    @Test
    void tiendaDesactivadaOInexistenteResponde404TIE404() throws Exception {
        Sesion propietario = nuevaSesion();
        Tienda tienda = tiendaOperativa(propietario);
        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Prueba de vitrina"),
                propietario.token(), tienda.id()).andExpect(status().isOk());

        getConToken(Rutas.vitrinaTarifasEnvio(tienda.subdominio()), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
        getConToken(Rutas.vitrinaTarifasEnvio("no-existe-" + sufijo()), null, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TIE-404"));
    }
}
