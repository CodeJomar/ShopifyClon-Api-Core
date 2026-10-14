package shopify.api.core.modulos.orden.modelo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Matriz completa de los mapas de transición: cada par origen → destino, sí o no. */
class EstadosTransicionTest {

    @Test
    void ordenSoloPermiteAbiertaACerradaOCanceladaYReabrirCerrada() {
        Set<List<EstadoOrden>> permitidas = Set.of(
                List.of(EstadoOrden.ABIERTA, EstadoOrden.CERRADA),
                List.of(EstadoOrden.ABIERTA, EstadoOrden.CANCELADA),
                List.of(EstadoOrden.CERRADA, EstadoOrden.ABIERTA));

        for (EstadoOrden origen : EstadoOrden.values()) {
            for (EstadoOrden destino : EstadoOrden.values()) {
                assertThat(origen.puedeTransicionarA(destino))
                        .as(origen + " → " + destino)
                        .isEqualTo(permitidas.contains(List.of(origen, destino)));
            }
        }
    }

    @Test
    void pagoSigueElCicloDeCobroYReembolso() {
        Set<List<EstadoPago>> permitidas = Set.of(
                List.of(EstadoPago.PENDIENTE, EstadoPago.PAGADO),
                List.of(EstadoPago.PENDIENTE, EstadoPago.ANULADO),
                List.of(EstadoPago.PAGADO, EstadoPago.PARCIALMENTE_REEMBOLSADO),
                List.of(EstadoPago.PAGADO, EstadoPago.REEMBOLSADO),
                List.of(EstadoPago.PARCIALMENTE_REEMBOLSADO, EstadoPago.PARCIALMENTE_REEMBOLSADO),
                List.of(EstadoPago.PARCIALMENTE_REEMBOLSADO, EstadoPago.REEMBOLSADO));

        for (EstadoPago origen : EstadoPago.values()) {
            for (EstadoPago destino : EstadoPago.values()) {
                assertThat(origen.puedeTransicionarA(destino))
                        .as(origen + " → " + destino)
                        .isEqualTo(permitidas.contains(List.of(origen, destino)));
            }
        }
    }

    @Test
    void preparacionAvanzaDeAUnoSinSaltos() {
        Set<List<EstadoPreparacion>> permitidas = Set.of(
                List.of(EstadoPreparacion.NO_PREPARADO, EstadoPreparacion.PREPARADO),
                List.of(EstadoPreparacion.PREPARADO, EstadoPreparacion.ENTREGADO));

        for (EstadoPreparacion origen : EstadoPreparacion.values()) {
            for (EstadoPreparacion destino : EstadoPreparacion.values()) {
                assertThat(origen.puedeTransicionarA(destino))
                        .as(origen + " → " + destino)
                        .isEqualTo(permitidas.contains(List.of(origen, destino)));
            }
        }
    }

    @Test
    void soloLosPagosConDineroCobradoHabilitanCierreYPreparacion() {
        assertThat(EstadoPago.PAGADO.estaCobrado()).isTrue();
        assertThat(EstadoPago.PARCIALMENTE_REEMBOLSADO.estaCobrado()).isTrue();
        assertThat(EstadoPago.REEMBOLSADO.estaCobrado()).isTrue();
        assertThat(EstadoPago.PENDIENTE.estaCobrado()).isFalse();
        assertThat(EstadoPago.ANULADO.estaCobrado()).isFalse();
    }
}
