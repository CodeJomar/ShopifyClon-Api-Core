package shopify.api.core.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import shopify.api.core.soporte.Rutas;

/** Dos carritos anónimos vinculados a la vez al mismo usuario: un solo carrito activo y sin errores 500. */
class CarritoConcurrenciaTest extends PruebaConcurrenciaBase {

    @BeforeEach
    void preparar() {
        prepararTienda();
    }

    @Test
    void dosVincularSimultaneosDelMismoUsuarioDejanUnSoloCarritoActivo() throws Exception {
        UUID variante = variante(new BigDecimal("10.00"), 20, false);
        String base = Rutas.vitrinaCarrito(subdominio);
        UUID usuario = crearUsuarioActivo(false);
        String correo = jdbc.queryForObject("SELECT \"CorreoElectronico\" FROM \"Usuarios\" WHERE \"Id\" = ?", String.class, usuario);
        String jwt = tokenPara(usuario, correo);
        String primero = carritoConLinea(base, variante);
        String segundo = carritoConLinea(base, variante);

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> estado(vincular(base, primero, jwt)),
                () -> estado(vincular(base, segundo, jwt))));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        assertThat(resultados).extracting(Simultaneo.Resultado::valor).containsOnly(200);
        Integer activos = jdbc.queryForObject("SELECT count(*) FROM \"Carritos\" WHERE \"TiendaId\" = ? AND \"UsuarioId\" = ? "
                + "AND \"Estado\" = 'ACTIVO' AND \"Eliminado\" = FALSE", Integer.class, tienda, usuario);
        assertThat(activos).isEqualTo(1);
    }

    private String carritoConLinea(String base, UUID variante) throws Exception {
        String token = cuerpo(mvc.perform(post(base)).andExpect(status().isCreated())).get("datos").get("token").asText();
        mvc.perform(post(base + "/items").contentType(MediaType.APPLICATION_JSON).header(CARRITO, token)
                        .content(json.writeValueAsString(Map.of("variante_id", variante, "cantidad", 1))))
                .andExpect(status().isOk());
        return token;
    }

    private ResultActions vincular(String base, String token, String jwt) throws Exception {
        return mvc.perform(post(base + "/vincular").header(CARRITO, token).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt));
    }
}
