package shopify.api.core.comun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import shopify.api.core.comun.seguridad.ContextoTienda;

@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_SEGURIDAD = "BearerAuth";
    public static final String PARAMETRO_TIENDA = "XTiendaId";

    static {
        // El contexto de tienda lo resuelve el servidor; no es un parámetro del contrato HTTP
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(ContextoTienda.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shopify Clon API Core")
                        .description("API REST transaccional para plataforma de comercio electrónico multi-tienda")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo de Desarrollo")
                                .email("soporte@shopifyclon.com"))
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_SEGURIDAD))
                .components(new Components()
                        .addParameters(PARAMETRO_TIENDA, new Parameter()
                                .in("header")
                                .name("X-Tienda-Id")
                                .required(false)
                                .description("Tienda sobre la que se opera (obligatorio en rutas del panel)")
                                .schema(new StringSchema().format("uuid")))
                        .addSecuritySchemes(ESQUEMA_SEGURIDAD, new SecurityScheme()
                                .name(ESQUEMA_SEGURIDAD)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Ingrese el token JWT obtenido en el login con el formato: Bearer {token}")));
    }
}
