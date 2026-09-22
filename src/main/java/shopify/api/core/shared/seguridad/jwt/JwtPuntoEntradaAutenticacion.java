package shopify.api.core.shared.seguridad.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import shopify.api.core.shared.exception.ErrorRespuesta;

import java.io.IOException;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtPuntoEntradaAutenticacion implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorRespuesta error = ErrorRespuesta.builder()
                .marcaTiempo(Instant.now())
                .codigoEstado(HttpStatus.UNAUTHORIZED.value())
                .error("No autorizado")
                .mensaje("Acceso denegado: Se requiere un token de autenticación válido para acceder a este recurso")
                .ruta(request.getRequestURI())
                .build();

        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}