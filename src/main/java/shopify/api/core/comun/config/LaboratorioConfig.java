package shopify.api.core.comun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve el laboratorio de endpoints (páginas HTML estáticas para probar la API a mano) en
 * {@code /api/v1/laboratorio/}. Es una herramienta de desarrollo: se apaga con
 * {@code app.laboratorio.habilitado=false}, y entonces esas rutas responden 404.
 */
@Configuration
@ConditionalOnProperty(name = "app.laboratorio.habilitado", havingValue = "true", matchIfMissing = true)
public class LaboratorioConfig implements WebMvcConfigurer {

    /** Por defecto el classpath; en desarrollo del laboratorio puede apuntar a una carpeta ({@code file:/ruta/}). */
    @Value("${app.laboratorio.ubicacion:classpath:/laboratorio/}")
    private String ubicacion;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/laboratorio/**").addResourceLocations(ubicacion);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        registro.addRedirectViewController("/laboratorio", "/laboratorio/index.html");
        registro.addRedirectViewController("/laboratorio/", "/laboratorio/index.html");
    }
}
