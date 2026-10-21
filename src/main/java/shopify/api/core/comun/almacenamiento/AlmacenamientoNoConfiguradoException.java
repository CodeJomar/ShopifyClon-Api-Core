package shopify.api.core.comun.almacenamiento;

import org.springframework.http.HttpStatus;
import shopify.api.core.comun.excepcion.ExcepcionNegocio;

/**
 * 503: el proveedor de almacenamiento no está configurado o no está disponible.
 * Se lanza cuando falta {@code SUPABASE_SECRET_KEY} o {@code SUPABASE_URL}.
 */
public class AlmacenamientoNoConfiguradoException extends ExcepcionNegocio {

    public AlmacenamientoNoConfiguradoException(String codigo, String mensaje) {
        super(codigo, HttpStatus.SERVICE_UNAVAILABLE, mensaje, null);
    }
}
