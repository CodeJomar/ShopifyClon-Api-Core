package shopify.api.core.soporte;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de las pruebas de integración: PostgreSQL real desechable (un único contenedor
 * compartido por toda la ejecución), esquema aplicado por Flyway y MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PruebaIntegracionBase {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicLong CONTADOR = new AtomicLong();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void propiedadesBaseDatos(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockBean
    protected JavaMailSender correo;

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Texto único por llamada para correos, subdominios y nombres de prueba. */
    protected String sufijo() {
        return Long.toString(System.nanoTime(), 36) + CONTADOR.incrementAndGet();
    }

    protected ResultActions postJson(String ruta, Object cuerpo, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.post(ruta), jwt, tiendaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    protected ResultActions putJson(String ruta, Object cuerpo, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.put(ruta), jwt, tiendaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    protected ResultActions getConToken(String ruta, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.get(ruta), jwt, tiendaId));
    }

    protected ResultActions delete(String ruta, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.delete(ruta), jwt, tiendaId));
    }

    private MockHttpServletRequestBuilder conCabeceras(MockHttpServletRequestBuilder peticion, String jwt, UUID tiendaId) {
        if (jwt != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        }
        if (tiendaId != null) {
            peticion.header("X-Tienda-Id", tiendaId.toString());
        }
        return peticion;
    }
}
