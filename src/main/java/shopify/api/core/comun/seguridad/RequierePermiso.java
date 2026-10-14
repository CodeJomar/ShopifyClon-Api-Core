package shopify.api.core.comun.seguridad;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declara el permiso que exige un método del panel; implica que la ruta exige {@code X-Tienda-Id}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequierePermiso {

    Permiso value();
}
