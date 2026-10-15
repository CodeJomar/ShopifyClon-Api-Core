package shopify.api.core.comun.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/** El id de correlación del hilo que publica llega al hilo asíncrono (criterio del MDC en @Async). */
class ProgramacionConfigTest {

    @Test
    void elIdDeCorrelacionLlegaAlHiloAsincrono() throws InterruptedException {
        AtomicReference<String> visto = new AtomicReference<>();
        MDC.put("correlacionId", "corr-prueba-42");
        Runnable trabajo = ProgramacionConfig.copiarMdc().decorate(() -> visto.set(MDC.get("correlacionId")));
        MDC.clear();

        Thread hilo = new Thread(trabajo);
        hilo.start();
        hilo.join();

        assertThat(visto.get()).isEqualTo("corr-prueba-42");
    }
}
