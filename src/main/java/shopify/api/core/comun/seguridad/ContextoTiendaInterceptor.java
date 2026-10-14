package shopify.api.core.comun.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import shopify.api.core.comun.excepcion.RecursoNoEncontradoException;
import shopify.api.core.comun.excepcion.SolicitudInvalidaException;

/**
 * Valida {@code X-Tienda-Id} contra la membresía del usuario (SEG-001), aplica {@code @RequierePermiso}
 * (SEG-002) y {@code @SoloAdministradorPlataforma} (SEG-006). Solo mira el encabezado en las rutas que
 * exigen tienda (con {@code @RequierePermiso} o un parámetro {@link ContextoTienda}).
 */
@Component
@RequiredArgsConstructor
public class ContextoTiendaInterceptor implements HandlerInterceptor {

    public static final String ENCABEZADO_TIENDA = "X-Tienda-Id";
    public static final String ATRIBUTO_CONTEXTO = ContextoTienda.class.getName();

    private final MembresiaTiendaRepositorio membresiaRepositorio;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }

        if (esSoloAdministradorPlataforma(metodo) && !SesionActual.esAdministradorPlataforma()) {
            throw new RecursoNoEncontradoException("SEG-006", "Recurso no encontrado");
        }

        RequierePermiso requierePermiso = metodo.getMethodAnnotation(RequierePermiso.class);
        boolean exigeTienda = requierePermiso != null || recibeContextoTienda(metodo);
        if (!exigeTienda) {
            // Una ruta que no exige tienda ignora el encabezado por completo
            return true;
        }

        UUID tiendaId = leerTiendaId(request)
                .orElseThrow(() -> new SolicitudInvalidaException("SEG-001", "Debes indicar la tienda en la que trabajas"));

        Optional<UsuarioAutenticado> usuario = SesionActual.usuario();
        if (usuario.isEmpty()) {
            return true;
        }

        ContextoTienda contexto = membresiaRepositorio.buscarCodigoRol(tiendaId, usuario.get().id())
                .flatMap(RolTienda::desdeCodigo)
                .map(rol -> new ContextoTienda(tiendaId, usuario.get().id(), rol))
                .orElseThrow(() -> new RecursoNoEncontradoException("SEG-001", "No encontramos esa tienda"));

        request.setAttribute(ATRIBUTO_CONTEXTO, contexto);

        if (requierePermiso != null) {
            contexto.exigir(requierePermiso.value());
        }
        return true;
    }

    private static Optional<UUID> leerTiendaId(HttpServletRequest request) {
        String valor = request.getHeader(ENCABEZADO_TIENDA);
        if (!StringUtils.hasText(valor)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(valor.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean esSoloAdministradorPlataforma(HandlerMethod metodo) {
        return metodo.hasMethodAnnotation(SoloAdministradorPlataforma.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), SoloAdministradorPlataforma.class);
    }

    private static boolean recibeContextoTienda(HandlerMethod metodo) {
        return Arrays.stream(metodo.getMethodParameters())
                .anyMatch(parametro -> ContextoTienda.class.equals(parametro.getParameterType()));
    }
}
