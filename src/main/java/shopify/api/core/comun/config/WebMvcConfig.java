package shopify.api.core.comun.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import shopify.api.core.comun.seguridad.ContextoTiendaInterceptor;
import shopify.api.core.comun.seguridad.ContextoTiendaResolver;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ContextoTiendaInterceptor contextoTiendaInterceptor;
    private final ContextoTiendaResolver contextoTiendaResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.contextoTiendaInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this.contextoTiendaResolver);
    }
}
