package shopify.api.core.comun.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve las fotos del catálogo de demostración ({@code /api/v1/semilla-fotos/**}) desde el propio jar, así los productos
 * de ejemplo tienen imágenes reales sin depender de un servicio externo. Son archivos estáticos y públicos.
 */
@Configuration
public class SemillaFotosConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/semilla-fotos/**")
                .addResourceLocations("classpath:/semilla-fotos/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
