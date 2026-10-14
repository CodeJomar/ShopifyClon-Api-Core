package shopify.api.core.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;

/** Inyecta el {@link ContextoTienda} validado por el interceptor en los parámetros de ese tipo. */
@Component
public class ContextoTiendaResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parametro) {
        return ContextoTienda.class.equals(parametro.getParameterType());
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parametro,
            ModelAndViewContainer contenedor,
            @NonNull NativeWebRequest peticion,
            WebDataBinderFactory fabrica) {
        HttpServletRequest http = peticion.getNativeRequest(HttpServletRequest.class);
        Object contexto = http == null ? null : http.getAttribute(ContextoTiendaInterceptor.ATRIBUTO_CONTEXTO);
        if (contexto == null) {
            throw new SolicitudInvalidaException("SEG-001", "Debes indicar la tienda en la que trabajas");
        }
        return contexto;
    }
}
