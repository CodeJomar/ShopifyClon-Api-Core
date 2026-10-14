package shopify.api.core.comun.observabilidad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import shopify.api.core.comun.dto.RespuestaApi;

/** Asigna un id de correlación a cada petición: lo toma de {@code X-Correlacion-Id} si es válido o lo genera. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelacionFiltro extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-Correlacion-Id";

    private static final Pattern FORMATO_VALIDO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String entrante = request.getHeader(ENCABEZADO);
        String id = entrante != null && FORMATO_VALIDO.matcher(entrante).matches()
                ? entrante
                : UUID.randomUUID().toString();

        MDC.put(RespuestaApi.CLAVE_CORRELACION_MDC, id);
        response.setHeader(ENCABEZADO, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RespuestaApi.CLAVE_CORRELACION_MDC);
        }
    }
}
