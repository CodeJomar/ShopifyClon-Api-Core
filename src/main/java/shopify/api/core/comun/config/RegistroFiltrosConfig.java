package shopify.api.core.comun.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shopify.api.core.comun.seguridad.jwt.JwtFiltroAutenticacion;

@Configuration
public class RegistroFiltrosConfig {

    /**
     * El filtro JWT ya va en la cadena de seguridad. Sin este registro deshabilitado, Spring Boot lo registraría también
     * como filtro de servlet y se ejecutaría dos veces por petición (revisión de T-002).
     */
    @Bean
    public FilterRegistrationBean<JwtFiltroAutenticacion> registroFiltroJwt(JwtFiltroAutenticacion filtro) {
        FilterRegistrationBean<JwtFiltroAutenticacion> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }
}
