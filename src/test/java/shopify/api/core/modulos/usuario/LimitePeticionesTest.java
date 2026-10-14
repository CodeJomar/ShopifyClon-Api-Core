package shopify.api.core.modulos.usuario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.Rutas;

@TestPropertySource(properties = "app.limite-peticiones.habilitado=true")
class LimitePeticionesTest extends PruebaUsuarioBase {

    private ResultActions loginDesde(String ip) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(Rutas.AUTH_LOGIN)
                .with(peticion -> {
                    peticion.setRemoteAddr(ip);
                    return peticion;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(
                        Map.of("correo_electronico", nuevoCorreo(), "contrasena", "Incorrecta1"))));
    }

    @Test
    void elUndecimoLoginDeLaMismaIpResponde429() throws Exception {
        String ip = "10.20." + (System.nanoTime() % 200) + ".1";
        for (int i = 0; i < 10; i++) {
            loginDesde(ip).andExpect(status().isUnauthorized());
        }

        var respuesta = loginDesde(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("GEN-429"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse();

        assertThat(Long.parseLong(respuesta.getHeader("Retry-After"))).isBetween(1L, 60L);

        // otra IP no se ve afectada
        loginDesde("10.99.99.99").andExpect(status().isUnauthorized());
    }
}
