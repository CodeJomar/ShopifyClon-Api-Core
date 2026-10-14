package shopify.api.core.modulos.tienda;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Utilidades de las pruebas del módulo tienda: usuarios con token y tiendas creadas por la API. */
abstract class PruebaTiendaBase extends PruebaIntegracionBase {

    /** Usuario activo con su token de sesión. */
    protected record Sesion(UUID id, String correo, String token) {
    }

    protected Sesion nuevaSesion() {
        UUID id = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
        return new Sesion(id, correo, tokenPara(id, correo));
    }

    protected String subdominioUnico() {
        return "tienda-" + sufijo();
    }

    protected Map<String, Object> cuerpoTienda(String subdominio) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Tienda " + subdominio);
        cuerpo.put("subdominio", subdominio);
        cuerpo.put("correo_contacto", "contacto@prueba.test");
        return cuerpo;
    }

    /** Crea una tienda por la API y devuelve su id. */
    protected UUID crearTiendaPorApi(Sesion propietario, String subdominio) throws Exception {
        JsonNode cuerpo = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda(subdominio), propietario.token(), null)
                .andExpect(status().isCreated()));
        return UUID.fromString(cuerpo.get("datos").get("id").asText());
    }

    /** Agrega a una persona al personal directamente en la base, para preparar escenarios. */
    protected Sesion personalConRol(UUID tiendaId, String codigoRol) {
        Sesion sesion = nuevaSesion();
        agregarPersonal(tiendaId, sesion.id(), codigoRol);
        return sesion;
    }

    protected int contarAuditoria(UUID tiendaId, String accion) {
        Integer filas = jdbc.queryForObject(
                "SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? AND \"Accion\" = ?",
                Integer.class, tiendaId, accion);
        return filas == null ? 0 : filas;
    }
}
