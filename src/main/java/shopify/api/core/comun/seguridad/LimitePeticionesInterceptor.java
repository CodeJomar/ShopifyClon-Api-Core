package shopify.api.core.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Aplica {@link LimitarPeticiones} antes de resolver el contexto de tienda. */
@Component
@RequiredArgsConstructor
public class LimitePeticionesInterceptor implements HandlerInterceptor {

    private final LimitadorPeticiones limitador;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {
        if (handler instanceof HandlerMethod metodo) {
            LimitarPeticiones limite = metodo.getMethodAnnotation(LimitarPeticiones.class);
            if (limite != null) {
                Object patron = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                limitador.registrar(request.getRemoteAddr(),
                        patron == null ? request.getRequestURI() : patron.toString(), limite.porMinuto());
            }
        }
        return true;
    }
}
