package shopify.api.core.comun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import shopify.api.core.comun.util.OrigenesPermitidos;

@Configuration
public class CorsConfig {

    private final List<String> origenesPermitidos;

    public CorsConfig(@Value("${app.cors.origenes-permitidos:http://localhost:3000}") String origenes) {
        this.origenesPermitidos = OrigenesPermitidos.desdeCsv(origenes);
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // A-007: solo los orígenes configurados (CORS_ORIGENES_PERMITIDOS, separados por coma); nunca "*" con credenciales
        config.setAllowedOrigins(origenesPermitidos);
        config.setAllowedHeaders(Arrays.asList("Origin", "Content-Type", "Accept", "Authorization", "X-Requested-With", "X-Tienda-Id", "X-Carrito-Token", "Idempotency-Key"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}