package shopify.api.core.comun.seguridad.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import shopify.api.core.comun.seguridad.UsuarioAutenticado;
import shopify.api.core.comun.seguridad.UsuarioSesionRepositorio;

@Component
@RequiredArgsConstructor
public class JwtFiltroAutenticacion extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtProveedor jwtProveedor;
    private final UsuarioSesionRepositorio usuarioSesionRepositorio;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extraerTokenDelEncabezado(request);

        if (StringUtils.hasText(token) && jwtProveedor.esTokenValido(token)) {
            // Solo autentica si el usuario sigue existiendo y activo; si no, la ruta protegida responde 401 SEG-007
            usuarioSesionRepositorio.buscarActivoPorId(jwtProveedor.extraerUsuarioId(token))
                    .ifPresent(usuario -> autenticar(usuario, request));
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(UsuarioAutenticado usuario, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken autenticacion =
                new UsernamePasswordAuthenticationToken(usuario, null, Collections.emptyList());
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    private String extraerTokenDelEncabezado(HttpServletRequest request) {
        String encabezadoAuth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(encabezadoAuth) && encabezadoAuth.startsWith(PREFIJO_BEARER)) {
            return encabezadoAuth.substring(PREFIJO_BEARER.length());
        }
        return null;
    }
}
