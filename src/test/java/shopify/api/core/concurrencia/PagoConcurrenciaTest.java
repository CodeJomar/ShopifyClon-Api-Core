package shopify.api.core.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import shopify.api.core.soporte.Rutas;

/** Pagos simultáneos: inicio de pago en línea (un solo cargo pendiente) y reembolsos que superan lo cobrado. */
class PagoConcurrenciaTest extends PruebaConcurrenciaBase {

    @BeforeEach
    void preparar() {
        prepararTienda();
    }

    @Test
    void dosInicioDePagoEnLineaSimultaneosDejanUnSoloCargoPendiente() throws Exception {
        UUID variante = variante(new BigDecimal("100.00"), 10, false);
        Compra compra = comprar(variante, 1, "STRIPE", new BigDecimal("100.00"));
        String pagar = Rutas.vitrinaOrdenes(subdominio) + "/" + compra.token() + "/pagar";

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> secretoDelPago(pagar),
                () -> secretoDelPago(pagar)));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        assertThat(resultados).extracting(Simultaneo.Resultado::valor).doesNotContainNull();
        Integer pendientes = jdbc.queryForObject("SELECT count(*) FROM \"Pagos\" WHERE \"OrdenId\" = ? AND \"Estado\" = 'PENDIENTE' "
                + "AND \"PasarelaPago\" = 'STRIPE'", Integer.class, compra.orden());
        assertThat(pendientes).isEqualTo(1);
        // Ambos reciben el mismo intento: el segundo reutiliza el pendiente del primero
        assertThat(resultados.get(0).valor()).isEqualTo(resultados.get(1).valor());
    }

    @Test
    void dosReembolsosSimultaneosQueSuperanLoCobradoSoloUnoSeAprueba() throws Exception {
        UUID variante = variante(new BigDecimal("100.00"), 10, false);
        Compra compra = comprar(variante, 1, "MANUAL", new BigDecimal("100.00"));
        String admin = personal(tienda, "ADMINISTRADOR");
        pagarManual(compra.orden(), admin);

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> reembolsar(compra.orden(), "60.00", admin),
                () -> reembolsar(compra.orden(), "60.00", admin)));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        assertThat(resultados).extracting(Simultaneo.Resultado::valor).containsExactlyInAnyOrder(201, 422);
        BigDecimal aprobado = jdbc.queryForObject("SELECT COALESCE(SUM(\"Monto\"), 0) FROM \"Pagos\" WHERE \"OrdenId\" = ? "
                + "AND \"Tipo\" = 'REEMBOLSO' AND \"Estado\" = 'APROBADO'", BigDecimal.class, compra.orden());
        assertThat(aprobado).isEqualByComparingTo("60.00");
    }

    private String secretoDelPago(String ruta) throws Exception {
        JsonNode datos = cuerpo(mvc.perform(post(ruta)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .status().isOk())).get("datos");
        return datos.get("client_secret").asText();
    }

    private int reembolsar(UUID orden, String monto, String jwt) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("monto", new BigDecimal(monto));
        cuerpo.put("motivo", "Reembolso de prueba");
        return mvc.perform(post(Rutas.PANEL_ORDENES + "/" + orden + "/reembolsos")
                        .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .header("X-Tienda-Id", tienda.toString()).content(json.writeValueAsString(cuerpo)))
                .andReturn().getResponse().getStatus();
    }
}
