package shopify.api.core.modulos.suscripcion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import shopify.api.core.modulos.suscripcion.modelo.EstadoSuscripcion;

/** Prueba unitaria del mapa de transiciones (suscripciones.md, "Estados y transiciones"). */
class EstadoSuscripcionTest {

    private static final EstadoSuscripcion PRUEBA = EstadoSuscripcion.PRUEBA;
    private static final EstadoSuscripcion ACTIVA = EstadoSuscripcion.ACTIVA;
    private static final EstadoSuscripcion VENCIDA = EstadoSuscripcion.VENCIDA;
    private static final EstadoSuscripcion CANCELADA = EstadoSuscripcion.CANCELADA;
    private static final EstadoSuscripcion SUSPENDIDA = EstadoSuscripcion.SUSPENDIDA;

    private static final Map<EstadoSuscripcion, Set<EstadoSuscripcion>> DESTINOS = Map.of(
            PRUEBA, EnumSet.of(ACTIVA, VENCIDA, CANCELADA, SUSPENDIDA),
            ACTIVA, EnumSet.of(ACTIVA, VENCIDA, CANCELADA, SUSPENDIDA),
            VENCIDA, EnumSet.of(ACTIVA, SUSPENDIDA),
            SUSPENDIDA, EnumSet.of(PRUEBA, ACTIVA, VENCIDA),
            CANCELADA, EnumSet.noneOf(EstadoSuscripcion.class));

    @Test
    void puedeTransicionarSoloA() {
        int combinaciones = 0;
        for (EstadoSuscripcion origen : EstadoSuscripcion.values()) {
            for (EstadoSuscripcion destino : EstadoSuscripcion.values()) {
                combinaciones++;
                assertThat(origen.puedeTransicionarA(destino))
                        .as("%s -> %s", origen, destino)
                        .isEqualTo(DESTINOS.get(origen).contains(destino));
            }
        }
        assertThat(combinaciones).isEqualTo(25);
    }

    @Test
    void soloPruebaYActivaPermitenOperar() {
        assertThat(PRUEBA.permiteOperar()).isTrue();
        assertThat(ACTIVA.permiteOperar()).isTrue();
        assertThat(VENCIDA.permiteOperar()).isFalse();
        assertThat(CANCELADA.permiteOperar()).isFalse();
        assertThat(SUSPENDIDA.permiteOperar()).isFalse();
    }
}
