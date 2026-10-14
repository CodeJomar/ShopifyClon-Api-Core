package shopify.api.core.comun.excepcion;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** 422: un dato viola una regla de negocio (cupón vencido, límite de plan). */
public class ReglaNegocioException extends ExcepcionNegocio {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje, null);
    }

    public ReglaNegocioException(String codigo, String mensaje, Map<String, Object> detalles) {
        super(codigo, HttpStatus.UNPROCESSABLE_ENTITY, mensaje, detalles);
    }

    /** Solo para los módulos viejos; el código nuevo debe indicar un código estable. */
    @Deprecated
    public ReglaNegocioException(String mensaje) {
        this("GEN-422", mensaje);
    }
}
