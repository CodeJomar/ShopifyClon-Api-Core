package shopify.api.core.concurrencia;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Ejecuta las tareas a la vez: todas esperan a un {@link CountDownLatch} y arrancan juntas. */
final class Simultaneo {

    /** Valor devuelto por la tarea o la excepción que lanzó. */
    record Resultado(Object valor, Throwable error) {
        boolean ok() {
            return error == null;
        }
    }

    private Simultaneo() {
    }

    static List<Resultado> ejecutar(List<Callable<?>> tareas) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Resultado>> futuros = new ArrayList<>();
            for (Callable<?> tarea : tareas) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    try {
                        return new Resultado(tarea.call(), null);
                    } catch (Throwable error) {
                        return new Resultado(null, error);
                    }
                }));
            }
            salida.countDown();
            List<Resultado> resultados = new ArrayList<>();
            for (Future<Resultado> futuro : futuros) {
                try {
                    resultados.add(futuro.get());
                } catch (java.util.concurrent.ExecutionException ex) {
                    resultados.add(new Resultado(null, ex.getCause()));
                }
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }
}
