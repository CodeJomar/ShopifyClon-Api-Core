package shopify.api.core.modulos.cupon.modelo;

/** Resultado de evaluar un cupón sin lanzar excepciones: o aplica, o trae el motivo. */
public record EvaluacionCupon(CuponAplicable aplicable, String motivo) {

    public boolean aplica() {
        return aplicable != null;
    }
}
