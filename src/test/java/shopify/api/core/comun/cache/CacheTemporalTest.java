package shopify.api.core.comun.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** La caché corta guarda el resultado durante su vida, no guarda errores y con 0 segundos nunca guarda. */
class CacheTemporalTest {

    /** Reloj de pruebas que solo avanza cuando se le pide. */
    private static final class RelojManual extends Clock {
        private final AtomicLong ahora = new AtomicLong(1_000_000L);

        void avanzarSegundos(long segundos) {
            ahora.addAndGet(segundos * 1000L);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(ahora.get());
        }
    }

    @Test
    void guardaElResultadoHastaQueVence() {
        RelojManual reloj = new RelojManual();
        CacheTemporal cache = new CacheTemporal(30, reloj);
        AtomicInteger llamadas = new AtomicInteger();

        assertThat(cache.obtener("k", () -> "v" + llamadas.incrementAndGet())).isEqualTo("v1");
        reloj.avanzarSegundos(29);
        assertThat(cache.obtener("k", () -> "v" + llamadas.incrementAndGet())).isEqualTo("v1");
        reloj.avanzarSegundos(2);
        assertThat(cache.obtener("k", () -> "v" + llamadas.incrementAndGet())).isEqualTo("v2");
        assertThat(llamadas.get()).isEqualTo(2);
    }

    @Test
    void clavesDistintasNoSeMezclanYVaciarOlvidaTodo() {
        CacheTemporal cache = new CacheTemporal(30, new RelojManual());
        assertThat(cache.obtener("a", () -> 1)).isEqualTo(1);
        assertThat(cache.obtener("b", () -> 2)).isEqualTo(2);
        assertThat(cache.obtener("a", () -> 99)).isEqualTo(1);

        cache.vaciar();

        assertThat(cache.obtener("a", () -> 99)).isEqualTo(99);
    }

    @Test
    void unErrorNoSeGuarda() {
        CacheTemporal cache = new CacheTemporal(30, new RelojManual());
        assertThatThrownBy(() -> cache.<String>obtener("k", () -> {
            throw new IllegalStateException("falla");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cache.obtener("k", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void conCeroSegundosSiempreConsulta() {
        CacheTemporal cache = new CacheTemporal(0, new RelojManual());
        AtomicInteger llamadas = new AtomicInteger();

        cache.obtener("k", llamadas::incrementAndGet);
        cache.obtener("k", llamadas::incrementAndGet);

        assertThat(llamadas.get()).isEqualTo(2);
    }
}
