package shopify.api.core.modulos.suscripcion.modelo;

import java.util.EnumSet;
import java.util.Set;

/** Estados de la suscripción de una tienda y sus transiciones (suscripciones.md, "Estados y transiciones"). */
public enum EstadoSuscripcion {

    PRUEBA,
    ACTIVA,
    VENCIDA,
    CANCELADA,
    SUSPENDIDA;

    /** Solo la prueba y la suscripción pagada dejan operar a la tienda. */
    public boolean permiteOperar() {
        return this == PRUEBA || this == ACTIVA;
    }

    public boolean puedeTransicionarA(EstadoSuscripcion destino) {
        return destinosPermitidos().contains(destino);
    }

    private Set<EstadoSuscripcion> destinosPermitidos() {
        return switch (this) {
            case PRUEBA, ACTIVA -> EnumSet.of(ACTIVA, VENCIDA, CANCELADA, SUSPENDIDA);
            case VENCIDA -> EnumSet.of(ACTIVA, SUSPENDIDA);
            case SUSPENDIDA -> EnumSet.of(PRUEBA, ACTIVA, VENCIDA);
            case CANCELADA -> EnumSet.noneOf(EstadoSuscripcion.class);
        };
    }
}
