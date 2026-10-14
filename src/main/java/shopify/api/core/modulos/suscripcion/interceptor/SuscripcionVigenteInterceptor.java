package shopify.api.core.modulos.suscripcion.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import shopify.api.core.comun.excepcion.ConflictoEstadoException;
import shopify.api.core.comun.seguridad.ContextoTienda;
import shopify.api.core.comun.seguridad.ContextoTiendaInterceptor;
import shopify.api.core.comun.seguridad.PermitidoSinSuscripcion;
import shopify.api.core.modulos.suscripcion.modelo.SuscripcionVigente;
import shopify.api.core.modulos.suscripcion.servicio.SuscripcionServicio;

/**
 * SUS-017: con la suscripción no vigente o que no permite operar, las escrituras del panel responden 409.
 * Corre después de {@link ContextoTiendaInterceptor}, que es quien resuelve el {@link ContextoTienda}.
 */
@Component
@RequiredArgsConstructor
public class SuscripcionVigenteInterceptor implements HandlerInterceptor {

    private static final Set<String> ESCRITURAS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SuscripcionServicio suscripciones;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        if (!(request.getAttribute(ContextoTiendaInterceptor.ATRIBUTO_CONTEXTO) instanceof ContextoTienda contexto)) {
            return true;
        }
        if (!ESCRITURAS.contains(request.getMethod()) || !request.getRequestURI().contains("/panel/")) {
            return true;
        }
        if (permitidoSinSuscripcion(metodo)) {
            return true;
        }

        boolean operable = suscripciones.vigente(contexto.tiendaId())
                .map(SuscripcionVigente::permiteOperar)
                .orElse(false);
        if (!operable) {
            throw new ConflictoEstadoException("SUS-017", "Tu suscripción no está activa. Paga para seguir operando");
        }
        return true;
    }

    private static boolean permitidoSinSuscripcion(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(PermitidoSinSuscripcion.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PermitidoSinSuscripcion.class);
    }
}
