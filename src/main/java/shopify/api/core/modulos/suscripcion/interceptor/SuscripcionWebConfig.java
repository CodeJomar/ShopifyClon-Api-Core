package shopify.api.core.modulos.suscripcion.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class SuscripcionWebConfig implements WebMvcConfigurer {

    /** El interceptor de tienda tiene orden 0; este va después para ver el contexto ya resuelto (SUS-017). */
    static final int ORDEN = 1;

    private final SuscripcionVigenteInterceptor suscripcionVigenteInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(suscripcionVigenteInterceptor).addPathPatterns("/**").order(ORDEN);
    }
}
