package shopify.api.core.modulos.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import shopify.api.core.soporte.Rutas;

/** R-030: el RUC es obligatorio al crear una tienda, válido (módulo 11) y único por propietario. */
class RucTiendaTest extends PruebaTiendaBase {

    private Map<String, Object> cuerpoConRuc(String ruc) {
        Map<String, Object> cuerpo = cuerpoTienda(subdominioUnico());
        if (ruc == null) {
            cuerpo.remove("ruc");
        } else {
            cuerpo.put("ruc", ruc);
        }
        return cuerpo;
    }

    private Map<String, Object> cambios(String ruc) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Nombre nuevo");
        cuerpo.put("correo_contacto", "nuevo@contacto.test");
        cuerpo.put("moneda", "USD");
        cuerpo.put("tasa_impuesto", "0.10");
        cuerpo.put("precios_incluyen_impuesto", false);
        cuerpo.put("zona_horaria", "America/Lima");
        if (ruc != null) {
            cuerpo.put("ruc", ruc);
        }
        return cuerpo;
    }

    private String rucDe(UUID tienda) {
        return jdbc.queryForObject("SELECT \"Ruc\" FROM \"Tiendas\" WHERE \"Id\" = ?", String.class, tienda);
    }

    @Test
    void elAltaExigeElRucYLoDevuelveEnTodasLasVistas() throws Exception {
        Sesion propietario = nuevaSesion();
        String ruc = rucUnico();

        JsonNode creada = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(ruc), propietario.token(), null)
                .andExpect(status().isCreated())).get("datos");
        assertThat(creada.get("ruc").asText()).isEqualTo(ruc);
        UUID tienda = UUID.fromString(creada.get("id").asText());
        assertThat(rucDe(tienda)).isEqualTo(ruc);

        getConToken(Rutas.PANEL_TIENDA, propietario.token(), tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value(ruc));
        JsonNode mias = cuerpo(getConToken(Rutas.CUENTA_TIENDAS, propietario.token(), null).andExpect(status().isOk())).get("datos");
        assertThat(mias.get(0).get("ruc").asText()).isEqualTo(ruc);
        JsonNode resumen = cuerpo(getConToken("/propietario/resumen", propietario.token(), null).andExpect(status().isOk())).get("datos");
        assertThat(resumen.get("tiendas").get(0).get("ruc").asText()).isEqualTo(ruc);
    }

    @Test
    void sinRucOConRucInvalidoSeRechazaConValidacionPorCampo() throws Exception {
        Sesion propietario = nuevaSesion();
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(null), propietario.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"))
                .andExpect(jsonPath("$.validaciones.ruc").exists());
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc("   "), propietario.token(), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.validaciones.ruc").exists());
        for (String malo : new String[]{"20100070971", "2010007097", "12345678901", "21100070970", "2010007097A", "20100070970000"}) {
            postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(malo), propietario.token(), null)
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.validaciones.ruc").exists());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"Tiendas\" WHERE \"PropietarioId\" = ?", Integer.class, propietario.id())).isZero();
    }

    @Test
    void elRucSeNormalizaYSeGuardaSinEspaciosNiGuiones() throws Exception {
        Sesion propietario = nuevaSesion();
        String ruc = rucUnico();
        String conFormato = ruc.substring(0, 2) + " " + ruc.substring(2, 10) + "-" + ruc.substring(10);

        JsonNode creada = cuerpo(postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(conFormato), propietario.token(), null)
                .andExpect(status().isCreated())).get("datos");

        assertThat(creada.get("ruc").asText()).isEqualTo(ruc);
    }

    @Test
    void elMismoPropietarioRepiteElRucPeroOtroPropietarioNo() throws Exception {
        Sesion propietario = nuevaSesion();
        String ruc = rucUnico();
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(ruc), propietario.token(), null).andExpect(status().isCreated());
        // Una segunda tienda de la misma persona con el mismo RUC es válida
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(ruc), propietario.token(), null).andExpect(status().isCreated());

        // Otra persona no puede usar ese RUC, ni con espacios o guiones
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(ruc), nuevaSesion().token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIE-010"));
        postJson(Rutas.CUENTA_TIENDAS, cuerpoConRuc(ruc.substring(0, 2) + "-" + ruc.substring(2, 10) + " " + ruc.substring(10)),
                nuevaSesion().token(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TIE-010"));
    }

    @Test
    void elPropietarioCorrigeElRucYNoPuedeUsarElDeOtraPersona() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        String nuevo = rucUnico();

        putJson(Rutas.PANEL_TIENDA, cambios(nuevo), propietario.token(), tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value(nuevo));
        assertThat(rucDe(tienda)).isEqualTo(nuevo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'TIENDA_RUC_CAMBIADO' "
                + "AND \"EntidadId\" = ?", Integer.class, tienda)).isEqualTo(1);

        // Sin RUC en el cuerpo se conserva el actual; con uno inválido, 422
        putJson(Rutas.PANEL_TIENDA, cambios(null), propietario.token(), tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value(nuevo));
        putJson(Rutas.PANEL_TIENDA, cambios("20100070971"), propietario.token(), tienda)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.validaciones.ruc").exists());

        // Un RUC de otra persona se rechaza; uno de otra tienda propia, no
        Sesion otra = nuevaSesion();
        UUID tiendaAjena = crearTiendaPorApi(otra, subdominioUnico());
        putJson(Rutas.PANEL_TIENDA, cambios(rucDe(tiendaAjena)), propietario.token(), tienda)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TIE-010"));
        // La segunda tienda de la persona (con suscripción vigente para poder escribir en el panel)
        UUID segunda = crearTiendaOperativa(propietario.id());
        putJson(Rutas.PANEL_TIENDA, cambios(nuevo), propietario.token(), segunda).andExpect(status().isOk());
    }

    @Test
    void soloElPropietarioCambiaElRuc() throws Exception {
        Sesion propietario = nuevaSesion();
        UUID tienda = crearTiendaPorApi(propietario, subdominioUnico());
        Sesion administrador = personalConRol(tienda, "ADMINISTRADOR");

        putJson(Rutas.PANEL_TIENDA, cambios(rucUnico()), administrador.token(), tienda)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("TIE-012"));
        // Mandar el mismo RUC no es un cambio: el administrador puede seguir editando lo demás
        putJson(Rutas.PANEL_TIENDA, cambios(rucDe(tienda)), administrador.token(), tienda).andExpect(status().isOk());
        putJson(Rutas.PANEL_TIENDA, cambios(null), administrador.token(), tienda).andExpect(status().isOk());
    }

    @Test
    void unaTiendaAnteriorSinRucSigueOperandoYSuDuenioLoCompleta() throws Exception {
        UUID duenio = crearUsuarioActivo(false);
        UUID tienda = crearTiendaOperativa(duenio);
        String token = tokenPara(duenio, jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, duenio));
        assertThat(rucDe(tienda)).isNull();

        // Sin RUC en el cuerpo la configuración se guarda igual y el RUC no aparece (ausente, no null)
        JsonNode sinRuc = cuerpo(putJson(Rutas.PANEL_TIENDA, cambios(null), token, tienda).andExpect(status().isOk())).get("datos");
        assertThat(sinRuc.has("ruc")).isFalse();
        assertThat(rucDe(tienda)).isNull();

        String ruc = rucUnico();
        putJson(Rutas.PANEL_TIENDA, cambios(ruc), token, tienda).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.ruc").value(ruc));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"Accion\" = 'TIENDA_RUC_CAMBIADO' "
                + "AND \"EntidadId\" = ?", Integer.class, tienda)).isEqualTo(1);
    }

    @Test
    void laBaseRechazaUnRucFueraDeFormatoOCompartidoEntreDosPropietarios() {
        UUID duenioA = crearUsuarioActivo(false);
        UUID a1 = crearTienda(duenioA);
        UUID a2 = crearTienda(duenioA);
        UUID b = crearTienda(crearUsuarioActivo(false));
        String ruc = rucUnico();

        jdbc.update("UPDATE \"Tiendas\" SET \"Ruc\" = ? WHERE \"Id\" = ?", ruc, a1);
        // El mismo propietario sí puede repetirlo
        assertThat(jdbc.update("UPDATE \"Tiendas\" SET \"Ruc\" = ? WHERE \"Id\" = ?", ruc, a2)).isEqualTo(1);
        // Otro propietario no, ni con un formato malo
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"Tiendas\" SET \"Ruc\" = ? WHERE \"Id\" = ?", ruc, b));
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE \"Tiendas\" SET \"Ruc\" = 'ABC' WHERE \"Id\" = ?", b));
        // Las tiendas eliminadas liberan el RUC
        jdbc.update("UPDATE \"Tiendas\" SET \"Eliminado\" = TRUE WHERE \"Id\" IN (?, ?)", a1, a2);
        assertThat(jdbc.update("UPDATE \"Tiendas\" SET \"Ruc\" = ? WHERE \"Id\" = ?", ruc, b)).isEqualTo(1);
    }
}
