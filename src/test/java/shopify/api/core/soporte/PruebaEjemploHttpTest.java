package shopify.api.core.soporte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Demuestra que la base HTTP funciona. Se reemplaza en T-003. */
class PruebaEjemploHttpTest extends PruebaIntegracionBase {

    @Test
    void registroCreaUnUsuario() throws Exception {
        String correo = "ejemplo-" + sufijo() + "@prueba.test";

        postJson("/usuarios/registro", Map.of(
                "correo_electronico", correo,
                "contrasena", "Clave12345!",
                "nombres", "Ana",
                "apellidos", "Prueba"), null, null)
                .andExpect(status().is2xxSuccessful());

        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM \"Usuarios\" WHERE \"CorreoElectronico\" = ?", Integer.class, correo);
        assertThat(filas).isEqualTo(1);
    }
}
