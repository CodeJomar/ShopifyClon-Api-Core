package shopify.api.core.comun.excepcion;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 409: transición no permitida, stock insuficiente, idempotencia en curso, etc. */
public class ConflictoEstadoException extends ExcepcionNegocio {

    public ConflictoEstadoException(String codigo, String mensaje) {
        super(codigo, HttpStatus.CONFLICT, mensaje, null);
    }

    public ConflictoEstadoException(String codigo, String mensaje, Map<String, Object> detalles) {
        super(codigo, HttpStatus.CONFLICT, mensaje, detalles);
    }
}
