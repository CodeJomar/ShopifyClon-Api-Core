package shopify.api.core.modulos.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import shopify.api.core.modulos.inventario.modelo.LineaInventario;
import shopify.api.core.modulos.inventario.servicio.InventarioServicio;
import shopify.api.core.soporte.Rutas;

class InventarioPanelTest extends PruebaInventarioBase {

    @Autowired
    private InventarioServicio inventario;

    @Autowired
    private TransactionTemplate transaccion;

    private UUID propietarioId;
    private String tokenPropietario;
    private String correoPropietario;
    private UUID tienda;

    @BeforeEach
    void preparar() {
        propietarioId = crearUsuarioActivo(false);
        correoPropietario = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?",
                String.class, propietarioId);
        tokenPropietario = tokenPara(propietarioId, correoPropietario);
        tienda = crearTiendaOperativa(propietarioId);
    }

    private String ruta(UUID varianteId) {
        return Rutas.PANEL_INVENTARIO_VARIANTES + "/" + varianteId;
    }

    private void reservar(UUID varianteId, int cantidad) {
        transaccion.executeWithoutResult(estado ->
                inventario.reservar(tienda, List.of(new LineaInventario(varianteId, cantidad)), null));
    }

    private Map<String, Object> ajuste(int nueva, String motivo) {
        return Map.of("nueva_cantidad", nueva, "motivo", motivo);
    }

    @Test
    void ajustarPorDebajoDeLoReservadoEs422INV007() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);
        reservar(v, 3);

        putJson(ruta(v), ajuste(2, "Conteo físico"), tokenPropietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INV-007"));
        assertThat(disponible(v)).isEqualTo(5);
    }

    @Test
    void ajusteValidoCreaMovimientoYAuditaConElMotivo() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);

        JsonNode datos = cuerpo(putJson(ruta(v), ajuste(8, "Conteo físico"), tokenPropietario, tienda)
                .andExpect(status().isOk())).get("datos");

        assertThat(datos.get("disponible").asInt()).isEqualTo(8);
        assertThat(disponible(v)).isEqualTo(8);
        assertThat(movimientos(v, "AJUSTE")).isEqualTo(1);
        assertThat(auditoria(tienda, "INVENTARIO_AJUSTADO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"Motivo\" FROM \"RegistroAuditoria\" WHERE \"TiendaId\" = ? "
                + "AND \"Accion\" = 'INVENTARIO_AJUSTADO'", String.class, tienda)).isEqualTo("Conteo físico");
    }

    @Test
    void ajusteSinCambioNoEscribeNadaNiAudita() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);

        putJson(ruta(v), ajuste(5, "Sin cambios reales"), tokenPropietario, tienda).andExpect(status().isOk());

        assertThat(movimientosTotales(v)).isZero();
        assertThat(auditoria(tienda, "INVENTARIO_AJUSTADO")).isZero();
    }

    @Test
    void ajusteSinMotivoEs422VAL001() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);

        putJson(ruta(v), Map.of("nueva_cantidad", 7), tokenPropietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VAL-001"));
    }

    @Test
    void quitarLaVentaSinStockConDisponibleNegativoEs422INV007() throws Exception {
        UUID v = variante(tienda, true, 0, 0, true);
        reservar(v, 3);
        transaccion.executeWithoutResult(estado ->
                inventario.confirmarVenta(tienda, List.of(new LineaInventario(v, 3)), null));
        assertThat(disponible(v)).isEqualTo(-3);

        putJson(ruta(v) + "/configuracion", Map.of("permitir_venta_sin_stock", false),
                tokenPropietario, tienda)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("INV-007"));
    }

    @Test
    void configurarRastreoYVentaSinStockCambiaLaConfiguracion() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);

        JsonNode datos = cuerpo(putJson(ruta(v) + "/configuracion",
                Map.of("rastrea_inventario", false, "permitir_venta_sin_stock", true),
                tokenPropietario, tienda).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("rastrea_inventario").asBoolean()).isFalse();
        assertThat(datos.get("permitir_venta_sin_stock").asBoolean()).isTrue();
    }

    @Test
    void detalleMuestraDisponibleReservadaYVendible() throws Exception {
        UUID v = variante(tienda, true, 5, 2, false);

        JsonNode datos = cuerpo(getConToken(ruta(v), tokenPropietario, tienda).andExpect(status().isOk())).get("datos");

        assertThat(datos.get("disponible").asInt()).isEqualTo(5);
        assertThat(datos.get("reservada").asInt()).isEqualTo(2);
        assertThat(datos.get("vendible").asInt()).isEqualTo(3);
    }

    @Test
    void movimientosSeListanDeMasRecienteAMasAntiguo() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);
        reservar(v, 1);
        transaccion.executeWithoutResult(estado ->
                inventario.liberar(tienda, List.of(new LineaInventario(v, 1)), null));
        putJson(ruta(v), ajuste(9, "Reposición manual"), tokenPropietario, tienda).andExpect(status().isOk());

        JsonNode lista = cuerpo(getConToken(ruta(v) + "/movimientos", tokenPropietario, tienda)
                .andExpect(status().isOk())).get("datos").get("datos");

        assertThat(lista).hasSize(3);
        assertThat(lista.get(0).get("tipo").asText()).isEqualTo("AJUSTE");
        assertThat(lista.get(2).get("tipo").asText()).isEqualTo("RESERVA");
    }

    @Test
    void gestorDePedidosRecibe403() throws Exception {
        UUID v = variante(tienda, true, 5, 0, false);
        UUID gestorId = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, gestorId);
        agregarPersonal(tienda, gestorId, "GESTOR_PEDIDOS");

        getConToken(ruta(v), tokenPara(gestorId, correo), tienda).andExpect(status().isForbidden());
        putJson(ruta(v), ajuste(7, "Intento del gestor"), tokenPara(gestorId, correo), tienda)
                .andExpect(status().isForbidden());
    }

    @Test
    void varianteDeOtraTiendaEs404INV404() throws Exception {
        UUID ajena = variante(crearTiendaOperativa(propietarioId), true, 5, 0, false);

        getConToken(ruta(ajena), tokenPropietario, tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("INV-404"));
        putJson(ruta(ajena), ajuste(1, "Otra tienda"), tokenPropietario, tienda)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("INV-404"));
    }
}
