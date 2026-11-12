package shopify.api.core.modulos.propietario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import shopify.api.core.soporte.PruebaIntegracionBase;

/** R-001 B7: panel general del propietario con todas sus tiendas, sin tienda activa. */
class ResumenPropietarioTest extends PruebaIntegracionBase {

    private UUID duenio;
    private String jwt;
    private UUID tiendaA;
    private UUID tiendaB;

    @BeforeEach
    void preparar() {
        duenio = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, duenio);
        jwt = tokenPara(duenio, correo);
        tiendaA = crearTiendaOperativa(duenio);
        tiendaB = crearTiendaOperativa(duenio);
        jdbc.update("UPDATE \"Tiendas\" SET \"Moneda\" = 'USD' WHERE \"Id\" = ?", tiendaA);
        jdbc.update("UPDATE \"Tiendas\" SET \"Moneda\" = 'PEN' WHERE \"Id\" = ?", tiendaB);
    }

    private void variante(UUID tienda, int stock) {
        UUID producto = jdbc.queryForObject("INSERT INTO \"Productos\" (\"TiendaId\", \"Titulo\", \"IdentificadorUrl\", "
                + "\"EstaPublicado\") VALUES (?, 'Producto', ?, TRUE) RETURNING \"Id\"", UUID.class, tienda, "p-" + sufijo());
        UUID variante = jdbc.queryForObject("INSERT INTO \"VariantesProducto\" (\"ProductoId\", \"Titulo\", \"SKU\", "
                + "\"Precio\") VALUES (?, 'Por defecto', ?, 10) RETURNING \"Id\"", UUID.class, producto, "SKU-" + sufijo());
        jdbc.update("INSERT INTO \"InventarioVariante\" (\"VarianteId\", \"CantidadDisponible\", \"CantidadReservada\", "
                + "\"RastreaInventario\", \"PermitirVentaSinStock\") VALUES (?, ?, 0, TRUE, FALSE)", variante, stock);
    }

    @Test
    void listaSoloLasTiendasPropiasConTotalesPorMonedaYAlertas() throws Exception {
        variante(tiendaA, 2);
        variante(tiendaA, 50);
        crearTiendaOperativa(crearUsuarioActivo(false));

        JsonNode resumen = cuerpo(getConToken("/propietario/resumen", jwt, null).andExpect(status().isOk())).get("datos");

        assertThat(resumen.get("tiendas")).hasSize(2);
        assertThat(resumen.get("totales")).hasSize(2);
        assertThat(resumen.get("totales").get(0).get("moneda").asText()).isNotEqualTo(resumen.get("totales").get(1).get("moneda").asText());
        for (JsonNode tienda : resumen.get("tiendas")) {
            boolean esA = tienda.get("tienda_id").asText().equals(tiendaA.toString());
            assertThat(tienda.get("operativa").asBoolean()).isTrue();
            assertThat(tienda.get("ventas").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(tienda.get("alertas").get("stock_bajo").asInt()).isEqualTo(esA ? 1 : 0);
            assertThat(tienda.get("alertas").get("ordenes_por_preparar").asInt()).isZero();
            assertThat(tienda.get("estado_suscripcion").asText()).isNotBlank();
        }
    }

    @Test
    void rangoInvalidoYSinSesion() throws Exception {
        getConToken("/propietario/resumen?desde=2026-02-10&hasta=2026-01-01", jwt, null)
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("MET-001"));
        getConToken("/propietario/resumen", null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void unCompradorSinTiendasRecibeListasVacias() throws Exception {
        UUID comprador = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, comprador);

        JsonNode resumen = cuerpo(getConToken("/propietario/resumen", tokenPara(comprador, correo), null)
                .andExpect(status().isOk())).get("datos");

        assertThat(resumen.get("tiendas")).isEmpty();
        assertThat(resumen.get("totales")).isEmpty();
    }
}
