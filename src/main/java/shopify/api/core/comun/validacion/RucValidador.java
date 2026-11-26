package shopify.api.core.comun.validacion;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RucValidador implements ConstraintValidator<Ruc, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return ValidadorRuc.normalizar(valor) == null || ValidadorRuc.esValido(valor);
    }
}
