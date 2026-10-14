package shopify.api.core.comun.excepcion;

import org.springframework.http.HttpStatus;

/** 401: no hay usuario en sesión. */
public class NoAutenticadoException extends ExcepcionNegocio {

    public NoAutenticadoException(String codigo, String mensaje) {
        super(codigo, HttpStatus.UNAUTHORIZED, mensaje, null);
    }
}
