package shopify.api.core.modulos.orden.modelo;

/** Estado general de la orden (CHECK {@code ChkOrdenesEstado}). Nace {@link #ABIERTA}. */
public enum EstadoOrden {
    ABIERTA,
    CERRADA,
    CANCELADA
}
