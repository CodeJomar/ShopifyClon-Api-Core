package shopify.api.core.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * La ruta exige un permiso de plataforma (R-001, B9). Quien no administra la plataforma recibe 404 {@code SEG-006}; un
 * administrador sin ese permiso recibe 403 {@code SEG-002}. En el método manda sobre la clase.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequierePermisoPlataforma {

    PermisoPlataforma value();
}
