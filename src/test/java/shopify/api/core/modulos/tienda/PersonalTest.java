package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.Rutas;

class PersonalTest extends PruebaTiendaBase {

    private Sesion propietario;
    private UUID tienda;

    @BeforeEach
    void preparar() throws Exception {
        propietario = nuevaSesion();
        tienda = crearTiendaPorApi(propietario, subdominioUnico());
    }

    private Map<String, String> invitar(Sesion persona, String rol) {
        return Map.of("correo_electronico", persona.correo(), "rol", rol);
    }

    private Map<String, String> motivo() {
        return Map.of("motivo", "Motivo de prueba");
    }

    @Test
    void agregarUnUsuarioActivoLoDejaEnElPersonalYLoAudita() throws Exception {
        Sesion persona = nuevaSesion();

        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.usuario_id").value(persona.id().toString()))
                .andExpect(jsonPath("$.datos.rol").value("GESTOR_PEDIDOS"))
                .andExpect(jsonPath("$.datos.desde").exists());

        assertThat(contarAuditoria(tienda, "PERSONAL_AGREGADO")).isEqualTo(1);
        // y entra a la tienda con su rol
        getConToken(Rutas.PANEL_TIENDA, persona.token(), tienda).andExpect(status().isOk());
    }

    @Test
    void correoInexistenteOInactivoResponde422() throws Exception {
        postJson(Rutas.PANEL_PERSONAL,
                Map.of("correo_electronico", "nadie-" + sufijo() + "@prueba.test", "rol", "GESTOR_PEDIDOS"),
                propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PER-001"));

        Sesion inactivo = nuevaSesion();
        inactivarUsuario(inactivo.id());
        postJson(Rutas.PANEL_PERSONAL, invitar(inactivo, "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PER-001"));
    }

    @Test
    void agregarAlRepetidoOAlPropietarioResponde409() throws Exception {
        Sesion persona = nuevaSesion();
        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isCreated());

        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_CATALOGO"), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PER-006"));
        postJson(Rutas.PANEL_PERSONAL, invitar(propietario, "ADMINISTRADOR"), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PER-006"));
    }

    @Test
    void elTercerMiembroSuperaElLimiteDelPlanBasico() throws Exception {
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isCreated());
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "GESTOR_CATALOGO"), propietario.token(), tienda)
                .andExpect(status().isCreated());

        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "ADMINISTRADOR"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PER-002"));
    }

    @Test
    void sinSuscripcionVigenteNoSePuedeAgregarPersonal() throws Exception {
        // SUS-017 se evalúa antes que el límite del plan: sin suscripción vigente el panel no escribe
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"Estado\" = 'CANCELADA' WHERE \"TiendaId\" = ?", tienda);

        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SUS-017"));
    }

    @Test
    void propietarioPrimeroEnElListado() throws Exception {
        Sesion persona = nuevaSesion();
        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_PEDIDOS"), propietario.token(), tienda);

        JsonNode lista = cuerpo(getConToken(Rutas.PANEL_PERSONAL, propietario.token(), tienda)
                .andExpect(status().isOk())).get("datos");

        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).get("rol").asText()).isEqualTo("PROPIETARIO");
        assertThat(lista.get(0).get("usuario_id").asText()).isEqualTo(propietario.id().toString());
        assertThat(lista.get(1).get("rol").asText()).isEqualTo("GESTOR_PEDIDOS");
    }

    @Test
    void sinPermisoDePersonalNoSePuedeListarNiAgregar() throws Exception {
        Sesion gestor = personalConRol(tienda, "GESTOR_PEDIDOS");

        getConToken(Rutas.PANEL_PERSONAL, gestor.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("SEG-002"));
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "GESTOR_PEDIDOS"), gestor.token(), tienda)
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorNoGestionaAOtroAdministradorPeroSiAGestores() throws Exception {
        Sesion admin = personalConRol(tienda, "ADMINISTRADOR");
        Sesion otroAdmin = personalConRol(tienda, "ADMINISTRADOR");
        Sesion gestor = personalConRol(tienda, "GESTOR_PEDIDOS");
        // Con el plan BASICO (límite 2) ya están ocupados los cupos; se amplía solo para esta prueba
        jdbc.update("UPDATE \"SuscripcionesTienda\" SET \"PlanId\" = (SELECT \"Id\" FROM \"PlanesSuscripcion\" WHERE \"Codigo\" = 'AVANZADO') "
                + "WHERE \"TiendaId\" = ?", tienda);

        // agregar a un ADMINISTRADOR
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "ADMINISTRADOR"), admin.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PER-004"));
        // quitar a otro ADMINISTRADOR
        deleteJson(Rutas.panelPersonal(otroAdmin.id()), motivo(), admin.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PER-004"));
        // cambiar a otro ADMINISTRADOR, o ascender a alguien a ADMINISTRADOR
        putJson(Rutas.panelPersonal(otroAdmin.id()), Map.of("rol", "GESTOR_PEDIDOS", "motivo", "Motivo de prueba"),
                admin.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PER-004"));
        putJson(Rutas.panelPersonal(gestor.id()), Map.of("rol", "ADMINISTRADOR", "motivo", "Motivo de prueba"),
                admin.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("PER-004"));

        // agregar y quitar un gestor sí puede
        Sesion nuevo = nuevaSesion();
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevo, "GESTOR_PEDIDOS"), admin.token(), tienda)
                .andExpect(status().isCreated());
        deleteJson(Rutas.panelPersonal(nuevo.id()), motivo(), admin.token(), tienda)
                .andExpect(status().isOk());
    }

    @Test
    void elPropietarioNoSeTocaYNadieCambiaSuPropioRol() throws Exception {
        Sesion admin = personalConRol(tienda, "ADMINISTRADOR");

        deleteJson(Rutas.panelPersonal(propietario.id()), motivo(), admin.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-003"));
        putJson(Rutas.panelPersonal(propietario.id()), Map.of("rol", "GESTOR_PEDIDOS", "motivo", "Motivo de prueba"),
                admin.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-003"));
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "PROPIETARIO"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-003"));

        putJson(Rutas.panelPersonal(admin.id()), Map.of("rol", "GESTOR_PEDIDOS", "motivo", "Motivo de prueba"),
                admin.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-007"));
        deleteJson(Rutas.panelPersonal(admin.id()), motivo(), admin.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-007"));
        // el propietario tampoco se quita a sí mismo
        deleteJson(Rutas.panelPersonal(propietario.id()), motivo(), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PER-007"));
    }

    @Test
    void cambiarElRolAudita_yElMismoRolNoCambiaNada() throws Exception {
        Sesion persona = personalConRol(tienda, "GESTOR_PEDIDOS");

        putJson(Rutas.panelPersonal(persona.id()), Map.of("rol", "GESTOR_CATALOGO", "motivo", "Cambia de area"),
                propietario.token(), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("GESTOR_CATALOGO"));
        assertThat(contarAuditoria(tienda, "PERSONAL_ROL_CAMBIADO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"Motivo\" FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? "
                + "AND \"Accion\" = 'PERSONAL_ROL_CAMBIADO'", String.class, tienda)).isEqualTo("Cambia de area");

        putJson(Rutas.panelPersonal(persona.id()), Map.of("rol", "GESTOR_CATALOGO", "motivo", "Sin cambios aqui"),
                propietario.token(), tienda)
                .andExpect(status().isOk());
        assertThat(contarAuditoria(tienda, "PERSONAL_ROL_CAMBIADO")).isEqualTo(1);
    }

    @Test
    void quitarSinMotivoResponde422() throws Exception {
        Sesion persona = personalConRol(tienda, "GESTOR_PEDIDOS");

        deleteJson(Rutas.panelPersonal(persona.id()), Map.of(), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VAL-001"));
        deleteJson(Rutas.panelPersonal(persona.id()), Map.of("motivo", "no"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VAL-001"));
    }

    @Test
    void quitarEsEliminacionLogicaAuditadaYBloqueaElAcceso() throws Exception {
        Sesion persona = personalConRol(tienda, "GESTOR_PEDIDOS");
        getConToken(Rutas.PANEL_TIENDA, persona.token(), tienda).andExpect(status().isOk());

        deleteJson(Rutas.panelPersonal(persona.id()), Map.of("motivo", "Ya no trabaja aqui"), propietario.token(), tienda)
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT \"Eliminado\" FROM \"PersonalTienda\" WHERE \"TiendaId\" = ? AND \"UsuarioId\" = ?",
                Boolean.class, tienda, persona.id())).isTrue();
        assertThat(contarAuditoria(tienda, "PERSONAL_QUITADO")).isEqualTo(1);
        getConToken(Rutas.PANEL_TIENDA, persona.token(), tienda)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("SEG-001"));

        // objetivo que ya no está en el personal
        deleteJson(Rutas.panelPersonal(persona.id()), motivo(), propietario.token(), tienda)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PER-404"));
    }

    @Test
    void sePuedeVolverAAgregarAAlguienQuitadoConUnaFilaNueva() throws Exception {
        Sesion persona = nuevaSesion();
        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_PEDIDOS"), propietario.token(), tienda)
                .andExpect(status().isCreated());
        deleteJson(Rutas.panelPersonal(persona.id()), motivo(), propietario.token(), tienda).andExpect(status().isOk());

        postJson(Rutas.PANEL_PERSONAL, invitar(persona, "GESTOR_CATALOGO"), propietario.token(), tienda)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.rol").value("GESTOR_CATALOGO"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"PersonalTienda\" WHERE \"TiendaId\" = ? AND \"UsuarioId\" = ?",
                Integer.class, tienda, persona.id())).isEqualTo(2);
    }

    @Test
    void rolInvalidoResponde422() throws Exception {
        postJson(Rutas.PANEL_PERSONAL, invitar(nuevaSesion(), "SUPERJEFE"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.rol").exists());
    }
}
