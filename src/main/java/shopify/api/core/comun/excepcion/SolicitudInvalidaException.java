package shopify.api.core.comun.excepcion;

import org.springframework.http.HttpStatus;

/** 400: la petición está mal formada. */
public class SolicitudInvalidaException extends ExcepcionNegocio {

    public SolicitudInvalidaException(String codigo, String mensaje) {
        super(codigo, HttpStatus.BAD_REQUEST, mensaje, null);
    }
}
