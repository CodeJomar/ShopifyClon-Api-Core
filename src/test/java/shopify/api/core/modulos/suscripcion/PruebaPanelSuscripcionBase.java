package shopify.api.core.modulos.suscripcion;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.Rutas;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sesiones con token, tiendas creadas por la API y lecturas directas para las pruebas de suscripción. */
abstract class PruebaPanelSuscripcionBase extends PruebaSuscripcionBase {

    protected record Sesion(UUID id, String correo, String token) {
    }

    protected record Tienda(UUID id, String subdominio) {
    }

    protected Sesion nuevaSesion(boolean administradorPlataforma) {
        UUID id = crearUsuarioActivo(administradorPlataforma);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
        return new Sesion(id, correo, tokenPara(id, correo));
    }

    protected Sesion nuevaSesion() {
        return nuevaSesion(false);
    }

    /** Tienda creada por la API: la primera del propietario recibe prueba; las siguientes nacen VENCIDA (D-024). */
    protected Tienda tiendaPorApi(Sesion propietario) throws Exception {
        String subdominio = "sus-" + sufijo();
        Map<String, Object> cuerpoTienda = new LinkedHashMap<>();
        cuerpoTienda.put("nombre", "Tienda " + subdominio);
        cuerpoTienda.put("subdominio", subdominio);
        cuerpoTienda.put("correo_contacto", "contacto@prueba.test");
        JsonNode datos = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda, propietario.token(), null)
                .andExpect(status().isCreated())).get("datos");
        return new Tienda(UUID.fromString(datos.get("id").asText()), subdominio);
    }

    protected Map<String, Object> cuerpoPago(String ciclo, String tokenPago) {
        Map<String, Object> cuerpoPago = new LinkedHashMap<>();
        cuerpoPago.put("ciclo", ciclo);
        cuerpoPago.put("token_pago", tokenPago);
        return cuerpoPago;
    }

    protected Map<String, Object> cuerpoCambio(String codigoPlan, String ciclo, String tokenPago) {
        Map<String, Object> cuerpoCambio = new LinkedHashMap<>();
        cuerpoCambio.put("codigo_plan", codigoPlan);
        cuerpoCambio.put("ciclo", ciclo);
        cuerpoCambio.put("token_pago", tokenPago);
        return cuerpoCambio;
    }

    protected ResultActions accionPanel(String accion, Object cuerpo, Sesion sesion, Tienda tienda) throws Exception {
        return postJson(Rutas.PANEL_SUSCRIPCION + "/" + accion, cuerpo, sesion.token(), tienda.id());
    }

    protected JsonNode panel(Sesion sesion, Tienda tienda) throws Exception {
        return cuerpo(getConToken(Rutas.PANEL_SUSCRIPCION, sesion.token(), tienda.id())
                .andExpect(status().isOk())).get("datos");
    }

    protected Sesion personalConRol(UUID tiendaId, String codigoRol) {
        Sesion persona = nuevaSesion();
        agregarPersonal(tiendaId, persona.id(), codigoRol);
        return persona;
    }

    protected int cobros(UUID tiendaId, String estado) {
        return jdbc.queryForObject("SELECT count(*) FROM \"CobrosSuscripcion\" c "
                        + "JOIN \"SuscripcionesTienda\" s ON s.\"Id\" = c.\"SuscripcionId\" "
                        + "WHERE s.\"TiendaId\" = ? AND c.\"Estado\" = ?", Integer.class, tiendaId, estado);
    }

    protected int auditoria(UUID tiendaId, String accion) {
        return jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tiendaId, accion);
    }

    /** Código de plan sin dígitos (el alta de la plataforma solo admite letras y guiones bajos). */
    protected String codigoPlanDeLetras() {
        StringBuilder codigo = new StringBuilder("PLAN_");
        for (int i = 0; i < 10; i++) {
            codigo.append((char) ('A' + ThreadLocalRandom.current().nextInt(26)));
        }
        return codigo.toString();
    }
}
