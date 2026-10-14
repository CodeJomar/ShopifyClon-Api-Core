package shopify.api.core.modulos.envio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class TarifaEnvioPanelTest extends PruebaEnvioBase {

    private Sesion propietario;
    private UUID tienda;

    @BeforeEach
    void preparar() {
        propietario = nuevaSesion();
        tienda = crearTiendaOperativa(propietario.id());
    }

    private UUID crear(Map<String, Object> cuerpo) throws Exception {
        JsonNode datos = cuerpo(postJson(Rutas.PANEL_ENVIOS_TARIFAS, cuerpo, propietario.token(), tienda)
                .andExpect(status().isCreated())).get("datos");
        return UUID.fromString(datos.get("id").asText());
    }

    private JsonNode listar() throws Exception {
        return cuerpo(getConToken(Rutas.PANEL_ENVIOS_TARIFAS, propietario.token(), tienda)
                .andExpect(status().isOk())).get("datos");
    }

    @Test
    void crudCompletoConEliminacionLogica() throws Exception {
        UUID id = crear(tarifaJson("Estándar", "12.50"));
        assertThat(listar()).hasSize(1);

        Map<String, Object> cambio = tarifaJson("Express", 20);
        cambio.put("monto_minimo_gratis", 100);
        JsonNode actualizada = cuerpo(putJson(Rutas.panelEnvioTarifa(id), cambio, propietario.token(), tienda)
                .andExpect(status().isOk())).get("datos");
        assertThat(actualizada.get("nombre").asText()).isEqualTo("Express");
        assertThat(new BigDecimal(actualizada.get("monto_minimo_gratis").asText())).isEqualByComparingTo("100.00");

        delete(Rutas.panelEnvioTarifa(id), propietario.token(), tienda).andExpect(status().isOk());
        assertThat(listar()).isEmpty();
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM \"TarifasEnvio\" WHERE \"Id\" = ? AND \"Eliminado\" = TRUE", Integer.class, id);
        assertThat(filas).isEqualTo(1);
    }

    @Test
    void precioNegativoEs422() throws Exception {
        postJson(Rutas.PANEL_ENVIOS_TARIFAS, tarifaJson("Negativa", -1), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
    }

    @Test
    void valoresPorDefectoOrdenCeroYActivaSiNoSeIndican() throws Exception {
        JsonNode datos = cuerpo(postJson(Rutas.PANEL_ENVIOS_TARIFAS, tarifaJson("Basica", "5"),
                propietario.token(), tienda).andExpect(status().isCreated())).get("datos");

        assertThat(datos.get("orden").asInt()).isZero();
        assertThat(datos.get("esta_activa").asBoolean()).isTrue();
    }

    @Test
    void listaPorOrdenYLuegoNombre() throws Exception {
        Map<String, Object> b = tarifaJson("B", "1");
        b.put("orden", 2);
        Map<String, Object> a = tarifaJson("A", "1");
        a.put("orden", 2);
        Map<String, Object> c = tarifaJson("C", "1");
        c.put("orden", 1);
        crear(b);
        crear(a);
        crear(c);

        List<String> nombres = new ArrayList<>();
        listar().forEach(tarifa -> nombres.add(tarifa.get("nombre").asText()));
        assertThat(nombres).containsExactly("C", "A", "B");
    }

    @Test
    void gestorDePedidosNoConfiguraEnvios() throws Exception {
        Sesion gestor = nuevaSesion();
        agregarPersonal(tienda, gestor.id(), "GESTOR_PEDIDOS");

        getConToken(Rutas.PANEL_ENVIOS_TARIFAS, gestor.token(), tienda).andExpect(status().isForbidden());
        postJson(Rutas.PANEL_ENVIOS_TARIFAS, tarifaJson("Ajena", "5"), gestor.token(), tienda)
                .andExpect(status().isForbidden());
    }

    @Test
    void tarifaDeOtraTiendaEs404() throws Exception {
        UUID id = crear(tarifaJson("Propia", "5"));
        UUID otra = crearTiendaOperativa(propietario.id());

        putJson(Rutas.panelEnvioTarifa(id), tarifaJson("Robada", "1"), propietario.token(), otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENV-404"));
        delete(Rutas.panelEnvioTarifa(id), propietario.token(), otra)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("ENV-404"));
    }
}
