package shopify.api.core.comun.excepcion;

import org.springframework.http.HttpStatus;

/** 403: el miembro no tiene el permiso. */
public class AccesoDenegadoException extends ExcepcionNegocio {

    public AccesoDenegadoException(String codigo, String mensaje) {
        super(codigo, HttpStatus.FORBIDDEN, mensaje, null);
    }
}
