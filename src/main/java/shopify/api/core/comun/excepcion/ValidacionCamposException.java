package shopify.api.core.comun.excepcion;

import java.util.Map;

/** Validación por campo que solo puede hacerse con datos del servidor: responde 422 VAL-001 con {@code validaciones}. */
public class ValidacionCamposException extends RuntimeException {

    private final transient Map<String, String> validaciones;

    public ValidacionCamposException(Map<String, String> validaciones) {
        super("Revisa los datos marcados");
        this.validaciones = Map.copyOf(validaciones);
    }

    public Map<String, String> getValidaciones() {
        return validaciones;
    }
}
