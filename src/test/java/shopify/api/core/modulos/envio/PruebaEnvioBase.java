package shopify.api.core.modulos.envio;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

/** Utilidades de las pruebas del módulo envío: usuarios con token, tiendas y tarifas preparadas. */
abstract class PruebaEnvioBase extends PruebaIntegracionBase {

    /** Usuario activo con su token de sesión. */
    protected record Sesion(UUID id, String correo, String token) {
    }

    /** Tienda creada por la API: queda operativa (activa y con prueba vigente). */
    protected record Tienda(UUID id, String subdominio) {
    }

    protected Sesion nuevaSesion() {
        UUID id = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
        return new Sesion(id, correo, tokenPara(id, correo));
    }

    protected Tienda tiendaOperativa(Sesion propietario) throws Exception {
        String subdominio = "env-" + sufijo();
        Map<String, Object> cuerpoTienda = new LinkedHashMap<>();
        cuerpoTienda.put("nombre", "Tienda " + subdominio);
        cuerpoTienda.put("subdominio", subdominio);
        cuerpoTienda.put("correo_contacto", "contacto@prueba.test");
        JsonNode datos = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoTienda, propietario.token(), null)
                .andExpect(status().isCreated())).get("datos");
        return new Tienda(UUID.fromString(datos.get("id").asText()), subdominio);
    }

    protected Map<String, Object> tarifaJson(String nombre, Object precio) {
        Map<String, Object> cuerpoTarifa = new LinkedHashMap<>();
        cuerpoTarifa.put("nombre", nombre);
        cuerpoTarifa.put("precio", precio);
        return cuerpoTarifa;
    }

    /** Inserta una tarifa directamente en la base y devuelve su id. */
    protected UUID insertarTarifa(UUID tiendaId, String nombre, String precio, boolean activa, int orden, boolean eliminada) {
        return jdbc.queryForObject("INSERT INTO \"TarifasEnvio\" (\"TiendaId\", \"Nombre\", \"Precio\", \"EstaActiva\", "
                        + "\"Orden\", \"Eliminado\") VALUES (?, ?, ?, ?, ?, ?) RETURNING \"Id\"", UUID.class,
                tiendaId, nombre, new BigDecimal(precio), activa, orden, eliminada);
    }
}
