package shopify.api.core.modulos.envio.modelo;

/** Estado del envío físico (CHECK {@code ChkEnviosEstado}). Nace {@link #CREADO}. */
public enum EstadoEnvio {
    CREADO,
    EN_TRANSITO,
    ENTREGADO,
    DEVUELTO;

    /** ENV-004: CREADO → EN_TRANSITO; EN_TRANSITO → ENTREGADO o DEVUELTO. Los demás son finales o no se alcanzan. */
    public boolean puedeTransicionarA(EstadoEnvio destino) {
        return switch (this) {
            case CREADO -> destino == EN_TRANSITO;
            case EN_TRANSITO -> destino == ENTREGADO || destino == DEVUELTO;
            case ENTREGADO, DEVUELTO -> false;
        };
    }
}
