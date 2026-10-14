package shopify.api.core.modulos.orden.modelo;

/** Estado general de la orden (CHECK {@code ChkOrdenesEstado}). Nace {@link #ABIERTA}. */
public enum EstadoOrden {
    ABIERTA,
    CERRADA,
    CANCELADA;

    /** ORD-004: ABIERTA ⇄ CERRADA (reapertura manual) y ABIERTA → CANCELADA. CANCELADA es final. */
    public boolean puedeTransicionarA(EstadoOrden destino) {
        return switch (this) {
            case ABIERTA -> destino == CERRADA || destino == CANCELADA;
            case CERRADA -> destino == ABIERTA;
            case CANCELADA -> false;
        };
    }
}
