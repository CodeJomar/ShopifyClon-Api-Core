package shopify.api.core.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** La ruta solo existe para administradores de plataforma; para el resto responde 404 {@code SEG-006}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface SoloAdministradorPlataforma {
}
