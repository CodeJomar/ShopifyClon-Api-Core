package shopify.api.core.concurrencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import shopify.api.core.modulos.pago.programador.OrdenVencimientoProgramador;

/** Dos ejecuciones simultáneas del programador de vencimientos: cada orden se anula una sola vez. */
class OrdenVencimientoConcurrenciaTest extends PruebaConcurrenciaBase {

    @Autowired
    private OrdenVencimientoProgramador programador;

    @BeforeEach
    void preparar() {
        prepararTienda();
    }

    @Test
    void dosEjecucionesSimultaneasAnulanCadaOrdenVencidaUnaSolaVez() throws Exception {
        UUID variante = variante(new BigDecimal("10.00"), 20, false);
        Compra primera = comprar(variante, 2, "MANUAL", new BigDecimal("20.00"));
        Compra segunda = comprar(variante, 2, "MANUAL", new BigDecimal("20.00"));
        jdbc.update("UPDATE \"Ordenes\" SET \"FechaExpiracionPago\" = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE \"Id\" IN (?, ?)",
                primera.orden(), segunda.orden());

        List<Simultaneo.Resultado> resultados = Simultaneo.ejecutar(List.of(
                () -> { programador.ejecutar(); return "ok"; },
                () -> { programador.ejecutar(); return "ok"; }));

        assertThat(resultados).allMatch(Simultaneo.Resultado::ok);
        for (UUID orden : List.of(primera.orden(), segunda.orden())) {
            assertThat(jdbc.queryForObject("SELECT \"Estado\" FROM \"Ordenes\" WHERE \"Id\" = ?", String.class, orden))
                    .isEqualTo("CANCELADA");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM \"RegistroAuditoria\" WHERE \"EntidadId\" = ? "
                    + "AND \"Accion\" = 'ORDEN_CANCELADA'", Integer.class, orden)).isEqualTo(1);
        }
        assertThat(reservada(variante)).isZero();
        assertThat(movimientos(variante, "LIBERACION")).isEqualTo(2);
    }
}
