package shopify.api.core.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Escritura del panel que sigue permitida sin suscripción vigente (SUS-017): gestionar la suscripción
 * y activar o desactivar la tienda.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PermitidoSinSuscripcion {
}
