package shopify.api.core.comun.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.excepcion.DemasiadasPeticionesException;

/**
 * Limitador de ventana fija de 60 s por clave (IP + ruta), en memoria del proceso (D-023).
 * Con varias instancias el límite es por instancia.
 */
@Component
@RequiredArgsConstructor
public class LimitadorPeticiones {

    private static final Duration VENTANA = Duration.ofSeconds(60);

    private final Clock reloj;

    @Value("${app.limite-peticiones.habilitado:true}")
    private boolean habilitado;

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    /** Registra una petición y lanza {@link DemasiadasPeticionesException} si supera {@code limite}. */
    public void registrar(String ip, String patronRuta, int limite) {
        if (!habilitado) {
            return;
        }
        Instant ahora = reloj.instant();
        ventanas.entrySet().removeIf(e -> !e.getValue().inicio().plus(VENTANA).isAfter(ahora));

        AtomicBoolean excedido = new AtomicBoolean(false);
        Ventana ventana = ventanas.compute(ip + " " + patronRuta, (clave, actual) -> {
            Ventana vigente = actual == null || !actual.inicio().plus(VENTANA).isAfter(ahora)
                    ? new Ventana(ahora, 0)
                    : actual;
            if (vigente.contador() >= limite) {
                excedido.set(true);
                return vigente;
            }
            return new Ventana(vigente.inicio(), vigente.contador() + 1);
        });

        if (excedido.get()) {
            long restantes = Duration.between(ahora, ventana.inicio().plus(VENTANA)).toSeconds();
            throw new DemasiadasPeticionesException(Math.max(restantes, 1));
        }
    }

    private record Ventana(Instant inicio, int contador) {
    }
}
