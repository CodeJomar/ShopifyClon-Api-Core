package shopify.api.core.shared.seguridad;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import shopify.api.core.shared.seguridad.jwt.JwtFiltroAutenticacion;
import shopify.api.core.shared.seguridad.jwt.JwtPuntoEntradaAutenticacion;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFiltroAutenticacion jwtFiltroAutenticacion;
    private final JwtPuntoEntradaAutenticacion jwtPuntoEntradaAutenticacion;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Deshabilitar CSRF para APIs REST sin estado
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())

                // Manejo de errores 401 personalizados
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(this.jwtPuntoEntradaAutenticacion)
                )

                // Sesión sin estado (Stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Reglas de autorización de endpoints
                .authorizeHttpRequests(auth -> auth
                        // 1. Endpoints de autenticación y registro
                        // En securityFilterChain:
                        .requestMatchers(
                                "/usuarios/registro",
                                "/usuarios/login",
                                "/usuarios/activar-cuenta",
                                "/usuarios/recuperar-clave/**"
                        ).permitAll()

                        // 2. Catálogo público de la tienda (Storefront)
                        .requestMatchers(HttpMethod.GET, "/catalogo/productos/**", "/catalogo/categorias/**").permitAll()

                        // 3. Webhooks de pago externos (ej: Stripe)
                        .requestMatchers("/pagos/webhook/**").permitAll()

                        // 4. Documentación OpenAPI / Swagger
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // Cualquier otra petición requiere autenticación por token JWT
                        .anyRequest().authenticated()
                )

                // Incorporación del filtro JWT antes del filtro por defecto de Spring
                .addFilterBefore(this.jwtFiltroAutenticacion, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}