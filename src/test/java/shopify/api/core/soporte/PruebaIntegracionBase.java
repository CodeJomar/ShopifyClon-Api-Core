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
import shopify.api.core.comun.seguridad.jwt.JwtProveedor;
import java.util.Map;
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

    @Autowired
    protected JwtProveedor jwtProveedor;

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

    protected ResultActions deleteJson(String ruta, Object cuerpo, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.delete(ruta), jwt, tiendaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }

    protected ResultActions delete(String ruta, String jwt, UUID tiendaId) throws Exception {
        return mvc.perform(conCabeceras(MockMvcRequestBuilders.delete(ruta), jwt, tiendaId));
    }

    // ---- Datos de prueba directos en la base (no dependen de ningún módulo) ----

    protected UUID crearUsuarioActivo(boolean adminPlataforma) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO \"Usuarios\" (\"Id\", \"CorreoElectronico\", \"ContrasenaHash\", \"Nombres\", "
                + "\"Apellidos\", \"EsAdministradorPlataforma\") VALUES (?, ?, 'hash-de-prueba', 'Prueba', 'Usuario', ?)",
                id, "u-" + sufijo() + "@prueba.test", adminPlataforma);
        return id;
    }

    protected void inactivarUsuario(UUID usuarioId) {
        jdbc.update("UPDATE \"Usuarios\" SET \"EstaActivo\" = FALSE WHERE \"Id\" = ?", usuarioId);
    }

    protected UUID crearTienda(UUID propietarioId) {
        UUID id = UUID.randomUUID();
        String sufijo = sufijo();
        jdbc.update("INSERT INTO \"Tiendas\" (\"Id\", \"PropietarioId\", \"Nombre\", \"Subdominio\", \"CorreoContacto\") "
                + "VALUES (?, ?, ?, ?, ?)", id, propietarioId, "Tienda " + sufijo, "t-" + sufijo, "t-" + sufijo + "@prueba.test");
        return id;
    }

    /**
     * Tienda con prueba vigente (plan BASICO, 14 días): las escrituras del panel pasan SUS-017.
     * {@link #crearTienda} sigue sin suscripción, para las pruebas del módulo suscripción que crean la suya.
     */
    protected UUID crearTiendaOperativa(UUID propietarioId) {
        UUID id = crearTienda(propietarioId);
        jdbc.update("INSERT INTO \"SuscripcionesTienda\" (\"TiendaId\", \"PlanId\", \"Estado\", \"CicloFacturacion\", "
                + "\"PrecioPactado\", \"Moneda\", \"FechaFinPrueba\", \"InicioPeriodoActual\", \"FinPeriodoActual\") "
                + "SELECT ?, \"Id\", 'PRUEBA', 'MENSUAL', \"PrecioMensual\", \"Moneda\", "
                + "CURRENT_TIMESTAMP + INTERVAL '14 days', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '14 days' "
                + "FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = 'BASICO'", id);
        return id;
    }

    protected void agregarPersonal(UUID tiendaId, UUID usuarioId, String codigoRol) {
        // Los roles retirados por V3 (CLIENTE, ADMIN_TIENDA) se siembran ya eliminados, como quedan tras la migración
        boolean retirado = codigoRol.equals("CLIENTE") || codigoRol.equals("ADMIN_TIENDA");
        jdbc.update("INSERT INTO \"Roles\" (\"Nombre\", \"Codigo\", \"Eliminado\") VALUES (?, ?, ?) "
                + "ON CONFLICT (\"Codigo\") DO NOTHING", codigoRol, codigoRol, retirado);
        jdbc.update("INSERT INTO \"PersonalTienda\" (\"TiendaId\", \"UsuarioId\", \"RolId\") "
                + "SELECT ?, ?, \"Id\" FROM \"Roles\" WHERE \"Codigo\" = ?", tiendaId, usuarioId, codigoRol);
    }

    protected void eliminarTienda(UUID tiendaId) {
        jdbc.update("UPDATE \"Tiendas\" SET \"Eliminado\" = TRUE WHERE \"Id\" = ?", tiendaId);
    }

    protected String tokenPara(UUID usuarioId, String correo) {
        return jwtProveedor.generarToken(usuarioId, correo, Map.of());
    }

    protected com.fasterxml.jackson.databind.JsonNode cuerpo(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
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
