package shopify.api.core.comun.cache;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caché en memoria de vida corta para lecturas públicas (mercado, vitrina, planes). Guarda el resultado ya armado
 * (DTOs inmutables) {@code app.cache.publico-segundos} segundos; con 0 se desactiva y siempre consulta. No se usa para
 * nada que dependa de la persona (reseñas propias, carrito, panel) ni para escrituras. Un dato nuevo (producto
 * publicado, reseña, stock) puede tardar hasta ese tiempo en verse en las listas públicas; el checkout siempre
 * revalida contra la base. Es por instancia: con varias instancias cada una tiene su propia caché.
 */
@Component
public class CacheTemporal {

    private static final int MAXIMO_ENTRADAS = 1000;

    private record Entrada(Object valor, long venceEn) {
    }

    private final ConcurrentHashMap<String, Entrada> entradas = new ConcurrentHashMap<>();
    private final long duracionMs;
    private final Clock reloj;

    @Autowired
    public CacheTemporal(@Value("${app.cache.publico-segundos:30}") long segundos, Clock reloj) {
        this.duracionMs = Math.max(segundos, 0) * 1000L;
        this.reloj = reloj;
    }

    /** Devuelve el valor vigente de la clave o lo calcula con {@code cargador} y lo guarda. */
    @SuppressWarnings("unchecked")
    public <T> T obtener(String clave, Supplier<T> cargador) {
        if (duracionMs == 0) {
            return cargador.get();
        }
        long ahora = reloj.millis();
        Entrada guardada = entradas.get(clave);
        if (guardada != null && guardada.venceEn() > ahora) {
            return (T) guardada.valor();
        }
        T valor = cargador.get();
        if (valor != null) {
            if (entradas.size() >= MAXIMO_ENTRADAS) {
                entradas.entrySet().removeIf(e -> e.getValue().venceEn() <= ahora);
                if (entradas.size() >= MAXIMO_ENTRADAS) {
                    entradas.clear();
                }
            }
            entradas.put(clave, new Entrada(valor, ahora + duracionMs));
        }
        return valor;
    }

    public void vaciar() {
        entradas.clear();
    }
}
