package shopify.api.core.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limita las peticiones por minuto, por IP y ruta (D-023). Al superarse responde 429 {@code GEN-429}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LimitarPeticiones {

    int porMinuto();
}
