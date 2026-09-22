package shopify.api.core.shared.multitenancy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String ENCABEZADO_TIENDA = "X-Tienda-Id";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        String tiendaIdStr = request.getHeader(ENCABEZADO_TIENDA);

        if (StringUtils.hasText(tiendaIdStr)) {
            try {
                TenantContextHolder.setTenantId(UUID.fromString(tiendaIdStr.trim()));
            } catch (IllegalArgumentException e) {
                // Si el formato del UUID no es válido, se ignora o se maneja según regla
                TenantContextHolder.clear();
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        // Garantiza que el hilo no conserve datos de otra tienda al reutilizarse
        TenantContextHolder.clear();
    }
}