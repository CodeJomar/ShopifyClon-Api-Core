package shopify.api.core.comun.validacion;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** El texto es un RUC válido (ver {@link ValidadorRuc}). Nulo o vacío pasa: la obligatoriedad la pone {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = RucValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Ruc {

    String message() default "El RUC no es válido: son 11 dígitos, empieza con 10, 15, 17 o 20 y su dígito verificador debe coincidir";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
