package shopify.api.core.comun.observabilidad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** A-007: el diagnóstico de red solo lo ve el administrador de plataforma. */
class DiagnosticoRedControladorTest extends PruebaIntegracionBase {

    private static final String RUTA = "/plataforma/diagnostico/red";

    @Test
    void elAdministradorDePlataformaVeSuIpYLasCabeceras() throws Exception {
        UUID admin = crearUsuarioActivo(true);

        mvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenPara(admin, "admin@prueba.test"))
                        .header("X-Real-IP", "190.12.34.56"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ip_conexion").exists())
                .andExpect(jsonPath("$.datos.ip_para_el_limite").exists())
                .andExpect(jsonPath("$.datos.cabeceras['X-Real-IP']").value("190.12.34.56"));
    }

    @Test
    void unUsuarioNormalRecibe404SinRevelarQueExiste() throws Exception {
        UUID usuario = crearUsuarioActivo(false);

        mvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenPara(usuario, "normal@prueba.test")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSesionRecibe401() throws Exception {
        mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }
}
