package shopify.api.core.modulos.administracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B9 etapa 1: roles de plataforma, administradores con rol y la regla de no conceder lo que no se tiene. */
class AdministradoresTest extends PruebaIntegracionBase {

    private UUID superId;
    private String superJwt;

    @BeforeEach
    void preparar() {
        superId = crearUsuarioActivo(true);
        superJwt = jwt(superId);
    }

    private String correo(UUID id) {
        return jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, id);
    }

    private String jwt(UUID id) {
        return tokenPara(id, correo(id));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder builder, String jwt, Object cuerpo) throws Exception {
        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).contentType(MediaType.APPLICATION_JSON);
        if (cuerpo != null) {
            builder.content(json.writeValueAsString(cuerpo));
        }
        return mvc.perform(builder);
    }

    private UUID rolDeSistema(String codigo) throws Exception {
        for (JsonNode rol : cuerpo(getConToken("/plataforma/roles", superJwt, null).andExpect(status().isOk())).get("datos")) {
            if (rol.get("codigo").asText().equals(codigo)) {
                return UUID.fromString(rol.get("id").asText());
            }
        }
        throw new AssertionError("No existe el rol " + codigo);
    }

    private UUID crearRol(String jwt, String codigo, List<String> permisos) throws Exception {
        return UUID.fromString(cuerpo(enviar(post("/plataforma/roles"), jwt,
                Map.of("codigo", codigo, "nombre", "Rol " + codigo, "permisos", permisos)).andExpect(status().isCreated()))
                .get("datos").get("id").asText());
    }

    private void hacerAdmin(UUID usuario, UUID rol) throws Exception {
        enviar(post("/plataforma/administradores"), superJwt, Map.of("usuario_id", usuario, "rol_id", rol))
                .andExpect(status().isCreated());
    }

    @Test
    void catalogoYRolesDeSistema() throws Exception {
        JsonNode permisos = cuerpo(getConToken("/plataforma/permisos", superJwt, null).andExpect(status().isOk())).get("datos");
        assertThat(permisos).hasSize(8);
        JsonNode roles = cuerpo(getConToken("/plataforma/roles", superJwt, null).andExpect(status().isOk())).get("datos");
        assertThat(roles.toString()).contains("ADMIN_PLATAFORMA", "SOPORTE", "FINANZAS");
        rolDeSistema("SOPORTE");
    }

    @Test
    void unAdministradorConRolSoloHaceLoQueSuRolPermite() throws Exception {
        UUID persona = crearUsuarioActivo(false);
        String jwt = jwt(persona);
        getConToken("/plataforma/tiendas", jwt, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("SEG-006"));

        hacerAdmin(persona, rolDeSistema("SOPORTE"));

        getConToken("/plataforma/tiendas", jwt, null).andExpect(status().isOk());
        getConToken("/plataforma/usuarios", jwt, null).andExpect(status().isOk());
        getConToken("/plataforma/planes", jwt, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEG-002"));
        getConToken("/plataforma/administradores", jwt, null).andExpect(status().isForbidden());
        enviar(post("/plataforma/usuarios/" + superId + "/cerrar-sesiones"), jwt, null).andExpect(status().isForbidden());

        JsonNode perfil = cuerpo(getConToken("/cuenta/perfil", jwt, null).andExpect(status().isOk())).get("datos");
        assertThat(perfil.get("es_administrador_plataforma").asBoolean()).isTrue();
        assertThat(perfil.get("acceso").get("rol_plataforma").get("codigo").asText()).isEqualTo("SOPORTE");
        assertThat(perfil.get("acceso").get("permisos_plataforma")).hasSize(4);

        // Quitarle la administración corta el acceso en la misma petición siguiente
        enviar(MockMvcRequestBuilders.delete("/plataforma/administradores/" + persona), superJwt, null).andExpect(status().isOk());
        getConToken("/plataforma/tiendas", jwt, null).andExpect(status().isNotFound());
    }

    @Test
    void nadieConcedeLoQueNoTiene() throws Exception {
        UUID gestorRol = crearRol(superJwt, "GESTOR_ADMINS", List.of("PLATAFORMA_ADMINISTRADORES", "PLATAFORMA_TIENDAS"));
        UUID gestor = crearUsuarioActivo(false);
        hacerAdmin(gestor, gestorRol);
        String jwt = jwt(gestor);

        enviar(post("/plataforma/roles"), jwt, Map.of("codigo", "CON_PLANES", "nombre", "x", "permisos", List.of("PLATAFORMA_PLANES")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ADM-001"));
        UUID otra = crearUsuarioActivo(false);
        enviar(post("/plataforma/administradores"), jwt, Map.of("usuario_id", otra, "rol_id", rolDeSistema("SOPORTE")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ADM-001"));

        UUID permitido = crearRol(jwt, "SOLO_TIENDAS", List.of("PLATAFORMA_TIENDAS"));
        enviar(post("/plataforma/administradores"), jwt, Map.of("usuario_id", otra, "rol_id", permitido))
                .andExpect(status().isCreated());
        // y no puede tocarse a sí mismo ni al superadministrador
        enviar(MockMvcRequestBuilders.delete("/plataforma/administradores/" + gestor), jwt, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-003"));
        enviar(MockMvcRequestBuilders.delete("/plataforma/administradores/" + superId), jwt, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-002"));
    }

    @Test
    void reglasDeLosRoles() throws Exception {
        UUID sistema = rolDeSistema("FINANZAS");
        enviar(put("/plataforma/roles/" + sistema), superJwt, Map.of("nombre", "x", "permisos", List.of("PLATAFORMA_TIENDAS")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-004"));
        enviar(MockMvcRequestBuilders.delete("/plataforma/roles/" + sistema), superJwt, null).andExpect(status().isConflict());

        UUID propio = crearRol(superJwt, "TEMPORAL", List.of("PLATAFORMA_AUDITORIA"));
        enviar(post("/plataforma/roles"), superJwt, Map.of("codigo", "TEMPORAL", "nombre", "x", "permisos", List.of("PLATAFORMA_AUDITORIA")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-006"));
        enviar(post("/plataforma/roles"), superJwt, Map.of("codigo", "RARO", "nombre", "x", "permisos", List.of("INVENTADO")))
                .andExpect(status().isBadRequest());

        UUID persona = crearUsuarioActivo(false);
        hacerAdmin(persona, propio);
        enviar(MockMvcRequestBuilders.delete("/plataforma/roles/" + propio), superJwt, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-005"));
        enviar(put("/plataforma/administradores/" + persona), superJwt, Map.of("rol_id", rolDeSistema("SOPORTE"))).andExpect(status().isOk());
        enviar(MockMvcRequestBuilders.delete("/plataforma/roles/" + propio), superJwt, null).andExpect(status().isOk());
        enviar(post("/plataforma/administradores"), superJwt, Map.of("usuario_id", persona, "rol_id", rolDeSistema("SOPORTE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ADM-007"));
    }

    @Test
    void cadaCambioQuedaAuditadoComoSeguridad() throws Exception {
        UUID persona = crearUsuarioActivo(false);
        hacerAdmin(persona, rolDeSistema("FINANZAS"));

        JsonNode eventos = cuerpo(getConToken("/plataforma/auditoria?accion=SEGURIDAD_ADMIN_AGREGADO&usuario_id=" + superId, superJwt, null)
                .andExpect(status().isOk())).get("datos");
        assertThat(eventos.get("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(eventos.get("datos").get(0).get("categoria").asText()).isEqualTo("SEGURIDAD");
    }

    @Test
    void unUsuarioCorrienteNoVeEstasRutas() throws Exception {
        String comun = jwt(crearUsuarioActivo(false));
        getConToken("/plataforma/administradores", comun, null).andExpect(status().isNotFound());
        getConToken("/plataforma/roles", comun, null).andExpect(status().isNotFound());
        getConToken("/plataforma/permisos", null, null).andExpect(status().isUnauthorized());
    }
}
