package shopify.api.core.comun.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import shopify.api.core.comun.dto.RespuestaApi;

/** Responde 403 {@code SEG-002} con el formato uniforme cuando Spring Security niega el acceso. */
@Component
@RequiredArgsConstructor
public class AccesoDenegadoManejador implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException excepcion) throws IOException {

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        RespuestaApi<Void> cuerpo = RespuestaApi.cuerpoError(HttpStatus.FORBIDDEN, "SEG-002",
                "Tu rol no tiene permiso para esta acción", request.getRequestURI(), null, null);
        response.getWriter().write(objectMapper.writeValueAsString(cuerpo));
    }
}
