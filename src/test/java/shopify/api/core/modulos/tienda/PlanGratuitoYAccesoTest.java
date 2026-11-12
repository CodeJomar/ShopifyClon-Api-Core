package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

/** R-001: plan gratuito (alta desde un comprador) y bloque {@code acceso} del perfil. */
class PlanGratuitoYAccesoTest extends PruebaTiendaBase {

    private Map<String, Object> cuerpoGratis(String subdominio) {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>(cuerpoTienda(subdominio));
        cuerpo.put("codigo_plan", "GRATIS");
        return cuerpo;
    }

    @Test
    void planesIncluyeElGratuitoMarcado() throws Exception {
        JsonNode planes = cuerpo(getConToken(Rutas.PLANES, null, null).andExpect(status().isOk())).get("datos");
        boolean encontrado = false;
        for (JsonNode plan : planes) {
            boolean gratis = plan.get("codigo").asText().equals("GRATIS");
            assertThat(plan.get("es_gratuito").asBoolean()).isEqualTo(gratis);
            encontrado |= gratis;
        }
        assertThat(encontrado).isTrue();
    }

    @Test
    void altaGratuitaNaceActivaYSoloUnaPorPersona() throws Exception {
        Sesion comprador = nuevaSesion();

        postJson(Rutas.CUENTA_TIENDAS, cuerpoGratis(subdominioUnico()), comprador.token(), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.suscripcion.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.datos.suscripcion.codigo_plan").value("GRATIS"))
                .andExpect(jsonPath("$.datos.operativa").value(true));

        postJson(Rutas.CUENTA_TIENDAS, cuerpoGratis(subdominioUnico()), comprador.token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-018"));
    }

    @Test
    void elPlanGratuitoNoSePagaNiSeEligeDespues() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = idTienda(postJson(Rutas.CUENTA_TIENDAS, cuerpoGratis(subdominioUnico()), propietario.token(), null)
                .andExpect(status().isCreated()));

        postJson(Rutas.PANEL_SUSCRIPCION + "/pagar",
                Map.of("ciclo", "MENSUAL", "token_pago", "tok_visa", "codigo_plan", "GRATIS"), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-019"));
        postJson(Rutas.PANEL_SUSCRIPCION + "/cambiar-plan",
                Map.of("codigo_plan", "GRATIS", "ciclo", "MENSUAL", "token_pago", "tok_visa"), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-019"));
    }

    @Test
    void cancelarElGratuitoEsInmediatoYLiberaElCupo() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = idTienda(postJson(Rutas.CUENTA_TIENDAS, cuerpoGratis(subdominioUnico()), propietario.token(), null)
                .andExpect(status().isCreated()));

        postJson(Rutas.PANEL_SUSCRIPCION + "/cancelar", Map.of("motivo", "Ya no la necesito"), propietario.token(), tienda)
                .andExpect(status().isOk());
        String estado = jdbc.queryForObject("SELECT \"Estado\" FROM \"SuscripcionesTienda\" WHERE \"TiendaId\" = ?",
                String.class, tienda);
        assertThat(estado).isEqualTo("CANCELADA");

        postJson(Rutas.CUENTA_TIENDAS, cuerpoGratis(subdominioUnico()), propietario.token(), null)
                .andExpect(status().isCreated());
    }

    @Test
    void perfilDeCompradorSinTiendasNoTienePermisos() throws Exception {
        Sesion comprador = nuevaSesion();

        JsonNode acceso = cuerpo(getConToken(Rutas.CUENTA_PERFIL, comprador.token(), null).andExpect(status().isOk()))
                .get("datos").get("acceso");
        assertThat(acceso.get("es_comprador").asBoolean()).isTrue();
        assertThat(acceso.get("es_propietario").asBoolean()).isFalse();
        assertThat(acceso.get("es_administrador_plataforma").asBoolean()).isFalse();
        assertThat(acceso.get("permisos_plataforma")).isEmpty();
        assertThat(acceso.get("tiendas")).isEmpty();
    }

    @Test
    void perfilDePropietarioYPersonalListaSusTiendasConPermisos() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        Sesion personal = personalConRol(tienda, "GESTOR_PEDIDOS");

        JsonNode dueno = cuerpo(getConToken(Rutas.CUENTA_PERFIL, propietario.token(), null).andExpect(status().isOk()))
                .get("datos").get("acceso");
        assertThat(dueno.get("es_propietario").asBoolean()).isTrue();
        assertThat(dueno.get("tiendas")).hasSize(1);
        assertThat(dueno.get("tiendas").get(0).get("es_propietario_de_la_tienda").asBoolean()).isTrue();
        assertThat(dueno.get("tiendas").get(0).get("permisos")).isNotEmpty();

        JsonNode empleado = cuerpo(getConToken(Rutas.CUENTA_PERFIL, personal.token(), null).andExpect(status().isOk()))
                .get("datos").get("acceso");
        assertThat(empleado.get("es_propietario").asBoolean()).isFalse();
        assertThat(empleado.get("tiendas")).hasSize(1);
        assertThat(empleado.get("tiendas").get(0).get("rol").asText()).isEqualTo("GESTOR_PEDIDOS");
    }

    @Test
    void administradorDePlataformaRecibeTodosLosPermisosDePlataforma() throws Exception {
        UUID admin = crearUsuarioActivo(true);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, admin);

        JsonNode acceso = cuerpo(getConToken(Rutas.CUENTA_PERFIL, tokenPara(admin, correo), null).andExpect(status().isOk()))
                .get("datos").get("acceso");
        assertThat(acceso.get("es_administrador_plataforma").asBoolean()).isTrue();
        assertThat(acceso.get("permisos_plataforma")).hasSizeGreaterThanOrEqualTo(8);
    }

    private UUID idTienda(org.springframework.test.web.servlet.ResultActions resultado) throws Exception {
        return UUID.fromString(cuerpo(resultado).get("datos").get("id").asText());
    }

}
