package shopify.api.core.modulos.suscripcion;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

/** SUS-017: con la suscripción no vigente, las escrituras del panel responden 409; las exentas siguen funcionando. */
class SuscripcionVigenteTest extends PruebaPanelSuscripcionBase {

    private Sesion propietario;
    private Tienda primera;
    private Tienda vencida;

    private void prepararTiendaVencida() throws Exception {
        propietario = nuevaSesion();
        primera = tiendaPorApi(propietario);
        vencida = tiendaPorApi(propietario);
    }

    @Test
    void tiendaVencidaRechazaLasEscriturasDelPanelConSus017() throws Exception {
        prepararTiendaVencida();

        putJson(Rutas.PANEL_TIENDA, Map.of("nombre", "Nueva"), propietario.token(), vencida.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-017"));

        Sesion persona = nuevaSesion();
        postJson(Rutas.PANEL_PERSONAL, Map.of("correo_electronico", persona.correo(), "rol", "GESTOR_PEDIDOS"),
                propietario.token(), vencida.id())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-017"));
    }

    @Test
    void tiendaVencidaSigueLeyendoYPuedePagar() throws Exception {
        prepararTiendaVencida();

        getConToken(Rutas.PANEL_TIENDA, propietario.token(), vencida.id()).andExpect(status().isOk());
        accionPanel("pagar", cuerpoPago("MENSUAL", "tok_ok"), propietario, vencida).andExpect(status().isOk());
    }

    @Test
    void desactivarLaTiendaSigueFuncionandoConSuscripcionVencida() throws Exception {
        prepararTiendaVencida();

        postJson(Rutas.PANEL_TIENDA_DESACTIVAR, Map.of("motivo", "Pausa temporal"), propietario.token(), vencida.id())
                .andExpect(status().isOk());
    }
}
