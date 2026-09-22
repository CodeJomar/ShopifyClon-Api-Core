package shopify.api.core.shared.seguridad.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtFiltroAutenticacion extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";
    private final JwtProveedor jwtProveedor;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extraerTokenDelEncabezado(request);

        if (StringUtils.hasText(token) && jwtProveedor.esTokenValido(token)) {
            UUID usuarioId = jwtProveedor.extraerUsuarioId(token);

            // Se establece el UUID del usuario como 'principal' (identificador) de autenticación
            UsernamePasswordAuthenticationToken autenticacion =
                    new UsernamePasswordAuthenticationToken(
                            usuarioId.toString(),
                            null,
                            Collections.emptyList() // Los roles o permisos pueden agregarse aquí
                    );

            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Permite que AuditorAwareImpl y los controladores obtengan el ID del usuario actual
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        }

        filterChain.doFilter(request, response);
    }

    private String extraerTokenDelEncabezado(HttpServletRequest request) {
        String encabezadoAuth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(encabezadoAuth) && encabezadoAuth.startsWith(PREFIJO_BEARER)) {
            return encabezadoAuth.substring(PREFIJO_BEARER.length());
        }
        return null;
    }
}