package shopify.api.core.modulos.orden.modelo;

/** Estado de preparación y entrega (CHECK {@code ChkOrdenesEstadoPreparacion}). Nace {@link #NO_PREPARADO}. */
public enum EstadoPreparacion {
    NO_PREPARADO,
    PREPARADO,
    ENTREGADO;

    /** ORD-006: NO_PREPARADO → PREPARADO → ENTREGADO, sin saltos ni retrocesos. */
    public boolean puedeTransicionarA(EstadoPreparacion destino) {
        return switch (this) {
            case NO_PREPARADO -> destino == PREPARADO;
            case PREPARADO -> destino == ENTREGADO;
            case ENTREGADO -> false;
        };
    }
}
