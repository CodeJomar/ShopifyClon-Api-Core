package shopify.api.core.shared.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class ErrorRespuesta {
    private Instant marcaTiempo;
    private int codigoEstado;
    private String error;
    private String mensaje;
    private String ruta;
    private Map<String, String> validaciones;
}