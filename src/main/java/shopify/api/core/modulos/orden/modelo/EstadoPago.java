package shopify.api.core.modulos.orden.modelo;

/** Estado del pago de la orden (CHECK {@code ChkOrdenesEstadoPago}). Nace {@link #PENDIENTE}. */
public enum EstadoPago {
    PENDIENTE,
    PAGADO,
    PARCIALMENTE_REEMBOLSADO,
    REEMBOLSADO,
    ANULADO
}
