package shopify.api.core.comun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;
import shopify.api.core.soporte.Rutas;

class SeguridadContextoTiendaTest extends PruebaIntegracionBase {

    private UUID propietario;
    private String tokenPropietario;
    private UUID tienda;

    @BeforeEach
    void preparar() {
        propietario = crearUsuarioActivo(false);
        tokenPropietario = tokenPara(propietario, "p@prueba.test");
        tienda = crearTienda(propietario);
    }

    @Test
    void sinTokenResponde401() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, null, tienda)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"))
                .andExpect(jsonPath("$.exito").value(false));
    }

    @Test
    void usuarioInactivadoResponde401() throws Exception {
        UUID usuario = crearUsuarioActivo(false);
        String token = tokenPara(usuario, "x@prueba.test");
        inactivarUsuario(usuario);

        getConToken(Rutas.PRUEBA_PANEL, token, tienda)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SEG-007"));
    }

    @Test
    void sinEncabezadoDeTiendaResponde400() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void encabezadoQueNoEsUuidResponde400() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(Rutas.PRUEBA_PANEL)
                        .header("Authorization", "Bearer " + tokenPropietario)
                        .header("X-Tienda-Id", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void tiendaDeOtroUsuarioResponde404() throws Exception {
        UUID otraTienda = crearTienda(crearUsuarioActivo(false));

        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, otraTienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void tiendaInexistenteResponde404() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void tiendaEliminadaResponde404() throws Exception {
        eliminarTienda(tienda);

        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void propietarioSinFilaDePersonalEntraComoPropietario() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("PROPIETARIO"))
                .andExpect(jsonPath("$.datos.tienda_id").value(tienda.toString()));
    }

    @Test
    void administradorEntra() throws Exception {
        UUID admin = crearUsuarioActivo(false);
        agregarPersonal(tienda, admin, "ADMINISTRADOR");

        getConToken(Rutas.PRUEBA_PANEL, tokenPara(admin, "a@prueba.test"), tienda)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("ADMINISTRADOR"));
    }

    @Test
    void gestorDeCatalogoNoTienePermisoDeCupones() throws Exception {
        UUID gestor = crearUsuarioActivo(false);
        agregarPersonal(tienda, gestor, "GESTOR_CATALOGO");
        String token = tokenPara(gestor, "g@prueba.test");

        getConToken(Rutas.PRUEBA_PANEL, token, tienda)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEG-002"));

        getConToken(Rutas.PRUEBA_PANEL_SIN_PERMISO, token, tienda)
                .andExpect(status().isOk());
    }

    @Test
    void rolClienteEnPersonalTiendaSeTrataComoSinMembresia() throws Exception {
        UUID cliente = crearUsuarioActivo(false);
        agregarPersonal(tienda, cliente, "CLIENTE");

        getConToken(Rutas.PRUEBA_PANEL_SIN_PERMISO, tokenPara(cliente, "c@prueba.test"), tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-001"));
    }

    @Test
    void rutaDePlataformaSoloParaAdministradores() throws Exception {
        UUID admin = crearUsuarioActivo(true);
        getConToken(Rutas.PRUEBA_PLATAFORMA, tokenPara(admin, "admin@prueba.test"), null)
                .andExpect(status().isOk());

        getConToken(Rutas.PRUEBA_PLATAFORMA, tokenPropietario, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("SEG-006"));
    }

    @Test
    void elContextoNoQuedaEnElHiloTrasLaPeticion() throws Exception {
        getConToken(Rutas.PRUEBA_PANEL, tokenPropietario, tienda).andExpect(status().isOk());

        @SuppressWarnings("deprecation")
        UUID residual = shopify.api.core.comun.seguridad.TenantContextHolder.getTenantId();
        assertThat(residual).isNull();
    }
}
