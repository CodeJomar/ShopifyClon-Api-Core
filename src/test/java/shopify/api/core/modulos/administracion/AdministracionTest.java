package shopify.api.core.modulos.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B8: administración de plataforma (tiendas, usuarios, auditoría de seguridad y pedidos). */
class AdministracionTest extends PruebaIntegracionBase {

    private UUID adminId;
    private String admin;
    private UUID duenioId;
    private String duenioCorreo;
    private String duenioJwt;
    private UUID tienda;
    private String subdominio;

    @BeforeEach
    void preparar() {
        adminId = crearUsuarioActivo(true);
        admin = tokenPara(adminId, correo(adminId));
        duenioId = crearUsuarioActivo(false);
        duenioCorreo = correo(duenioId);
        duenioJwt = tokenPara(duenioId, duenioCorreo);
        tienda = crearTiendaOperativa(duenioId);
        subdominio = jdbc.queryForObject("SELECT \"Subdominio\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    private String correo(UUID id) {
        return jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
    }

    private JsonNode leer(String ruta) throws Exception {
        return cuerpo(getConToken(ruta, admin, null).andExpect(status().isOk())).get("datos");
    }

    private ResultActions accion(String ruta, Object cuerpo) throws Exception {
        return mvc.perform(post(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo == null ? Map.of() : cuerpo)));
    }

    @Test
    void laBusquedaDeTiendasEncuentraTambienPorElNombreDelPropietario() throws Exception {
        String unico = "Zoilo" + sufijo().replace("-", "");
        jdbc.update("UPDATE \"Usuarios\" SET \"Nombres\" = ?, \"Apellidos\" = 'Quispe Mamani' "
                + "WHERE lower(\"CorreoElectronico\") = lower(?)", unico, duenioCorreo);

        for (String busqueda : new String[]{unico, unico.toLowerCase(), "quispe", "mamani", unico + " quispe"}) {
            JsonNode lista = leer("/plataforma/tiendas?q=" + busqueda);
            assertThat(lista.get("total").asInt()).as("busqueda: " + busqueda).isEqualTo(1);
            assertThat(lista.get("datos").get(0).get("subdominio").asText()).isEqualTo(subdominio);
        }
        assertThat(leer("/plataforma/tiendas?q=" + unico + "inexistente").get("total").asInt()).isZero();
    }

    @Test
    void listaYDetalleDeTiendasConDuenioYPlan() throws Exception {
        JsonNode lista = leer("/plataforma/tiendas?q=" + subdominio);
        assertThat(lista.get("total").asInt()).isEqualTo(1);
        JsonNode fila = lista.get("datos").get(0);
        assertThat(fila.get("subdominio").asText()).isEqualTo(subdominio);
        assertThat(fila.get("duenio").get("correo").asText()).isEqualTo(duenioCorreo);

        JsonNode detalle = leer("/plataforma/tiendas/" + tienda);
        assertThat(detalle.get("tienda").get("id").asText()).isEqualTo(tienda.toString());
        assertThat(detalle.has("equipo")).isTrue();

        assertThat(leer("/plataforma/tiendas?estado=INACTIVA&q=" + subdominio).get("total").asInt()).isZero();
        getConToken("/plataforma/tiendas?estado=raro", admin, null).andExpect(status().isBadRequest());
        getConToken("/plataforma/tiendas/" + UUID.randomUUID(), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void soloElAdministradorEntraAEstasRutas() throws Exception {
        getConToken("/plataforma/tiendas", duenioJwt, null).andExpect(status().isNotFound());
        getConToken("/plataforma/usuarios", duenioJwt, null).andExpect(status().isNotFound());
        getConToken("/plataforma/auditoria", duenioJwt, null).andExpect(status().isNotFound());
        getConToken("/plataforma/pedidos", duenioJwt, null).andExpect(status().isNotFound());
        getConToken("/plataforma/tiendas", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void bloquearCierraSesionesQuedaAuditadoYSePuedeDesbloquear() throws Exception {
        JsonNode usuario = leer("/plataforma/usuarios?q=" + duenioCorreo);
        assertThat(usuario.get("datos").get(0).get("tiendas_propias").asInt()).isEqualTo(1);
        assertThat(leer("/plataforma/usuarios/" + duenioId).get("tiendas")).hasSize(1);

        accion("/plataforma/usuarios/" + duenioId + "/bloquear", Map.of("motivo", "Actividad sospechosa")).andExpect(status().isOk());

        getConToken("/cuenta/perfil", duenioJwt, null).andExpect(status().isUnauthorized());
        assertThat(leer("/plataforma/usuarios?estado=BLOQUEADO&q=" + duenioCorreo).get("total").asInt()).isEqualTo(1);
        JsonNode eventos = leer("/plataforma/auditoria?categoria=SEGURIDAD&usuario_id=" + adminId);
        assertThat(eventos.get("total").asInt()).isGreaterThanOrEqualTo(1);
        JsonNode primero = eventos.get("datos").get(0);
        assertThat(primero.get("accion").asText()).isEqualTo("SEGURIDAD_USUARIO_BLOQUEADO");
        assertThat(primero.get("categoria").asText()).isEqualTo("SEGURIDAD");
        assertThat(primero.get("entidad_id").asText()).isEqualTo(duenioId.toString());

        accion("/plataforma/usuarios/" + duenioId + "/desbloquear", null).andExpect(status().isOk());
        assertThat(leer("/plataforma/usuarios?estado=BLOQUEADO&q=" + duenioCorreo).get("total").asInt()).isZero();
        accion("/plataforma/usuarios/" + duenioId + "/cerrar-sesiones", null).andExpect(status().isOk());
        assertThat(leer("/plataforma/auditoria?accion=SEGURIDAD_SESIONES_CERRADAS&usuario_id=" + adminId)
                .get("total").asInt()).isEqualTo(1);
    }

    @Test
    void nadieSeBloqueaASiMismoNiSinMotivo() throws Exception {
        accion("/plataforma/usuarios/" + adminId + "/bloquear", Map.of("motivo", "Prueba"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("USR-015"));
        accion("/plataforma/usuarios/" + duenioId + "/bloquear", Map.of("motivo", " ")).andExpect(status().isUnprocessableEntity());
        accion("/plataforma/usuarios/" + UUID.randomUUID() + "/desbloquear", null).andExpect(status().isNotFound());
    }

    @Test
    void auditoriaFiltraPorCategoriaYValida() throws Exception {
        getConToken("/plataforma/auditoria?categoria=raro", admin, null).andExpect(status().isBadRequest());
        getConToken("/plataforma/auditoria?desde=2026-02-10&hasta=2026-01-01", admin, null).andExpect(status().isBadRequest());
        JsonNode negocio = leer("/plataforma/auditoria?categoria=NEGOCIO");
        for (JsonNode registro : negocio.get("datos")) {
            assertThat(registro.get("categoria").asText()).isEqualTo("NEGOCIO");
        }
    }

    @Test
    void listaPedidosDeLaPlataformaConBusqueda() throws Exception {
        String numero = "P-T" + sufijo();
        jdbc.update("INSERT INTO \"Pedidos\" (\"NumeroPedido\", \"CorreoCliente\", \"NombreCliente\", \"Moneda\", \"Subtotal\", "
                + "\"Total\", \"TokenAcceso\") VALUES (?, 'cliente@prueba.test', 'Cliente', 'USD', 10, 10, ?)", numero, "tok-" + sufijo() + sufijo());

        JsonNode pedidos = leer("/plataforma/pedidos?q=" + numero.toLowerCase());

        assertThat(pedidos.get("total").asInt()).isEqualTo(1);
        assertThat(pedidos.get("datos").get(0).get("numero_pedido").asText()).isEqualTo(numero);
        assertThat(pedidos.get("datos").get(0).get("ordenes").asInt()).isZero();
        assertThat(leer("/plataforma/pedidos?q=%25").get("total").asInt()).isZero();
    }
}
