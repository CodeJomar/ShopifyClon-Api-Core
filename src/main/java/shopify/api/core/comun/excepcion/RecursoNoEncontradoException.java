package shopify.api.core.comun.excepcion;

import org.springframework.http.HttpStatus;

/** 404: no existe o es de otra tienda. */
public class RecursoNoEncontradoException extends ExcepcionNegocio {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(codigo, HttpStatus.NOT_FOUND, mensaje, null);
    }

    /** Solo para los módulos viejos; el código nuevo debe indicar un código estable. */
    @Deprecated
    public RecursoNoEncontradoException(String mensaje) {
        this("GEN-404", mensaje);
    }
}
