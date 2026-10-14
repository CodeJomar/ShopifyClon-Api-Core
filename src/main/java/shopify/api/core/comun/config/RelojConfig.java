package shopify.api.core.comun.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojConfig {

    /** Reloj inyectable (UTC) para poder probar vencimientos. */
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
