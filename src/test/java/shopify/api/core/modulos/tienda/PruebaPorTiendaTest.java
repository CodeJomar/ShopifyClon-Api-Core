package shopify.api.core.modulos.tienda;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import shopify.api.core.soporte.Rutas;

/** Con {@code app.suscripcion.prueba-una-por-usuario=false} cada tienda recibe su propia prueba (Q-015). */
@TestPropertySource(properties = "app.suscripcion.prueba-una-por-usuario=false")
class PruebaPorTiendaTest extends PruebaTiendaBase {

    @Test
    void laSegundaTiendaTambienRecibePrueba() throws Exception {
        Sesion propietario = nuevaSesion();
        crearTiendaPorApi(propietario, subdominioUnico());

        postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominioUnico()), propietario.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("PRUEBA"))
                .andExpect(jsonPath("$.datos.operativa").value(true));
    }
}
