package shopify.api.core.comun.config;

import java.util.Map;
import java.util.concurrent.Executor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableScheduling
public class ProgramacionConfig {

    /**
     * Ejecutor de {@code @Async}. Copia el MDC (id de correlación) al hilo del trabajo. Con {@code app.async.sincrono=true}
     * (pruebas) corre en el mismo hilo, para que los efectos sean deterministas.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor(@Value("${app.async.sincrono:false}") boolean sincrono) {
        if (sincrono) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(8);
        ejecutor.setQueueCapacity(200);
        ejecutor.setThreadNamePrefix("async-");
        ejecutor.setTaskDecorator(copiarMdc());
        return ejecutor;
    }

    /** Copia el contexto de diagnóstico del hilo que publica al hilo asíncrono, y lo limpia al terminar. */
    static TaskDecorator copiarMdc() {
        return ejecucion -> {
            Map<String, String> contexto = MDC.getCopyOfContextMap();
            return () -> {
                if (contexto != null) {
                    MDC.setContextMap(contexto);
                }
                try {
                    ejecucion.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
