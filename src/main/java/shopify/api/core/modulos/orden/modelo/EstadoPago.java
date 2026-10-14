package shopify.api.core.modulos.orden.modelo;

/** Estado del pago de la orden (CHECK {@code ChkOrdenesEstadoPago}). Nace {@link #PENDIENTE}. */
public enum EstadoPago {
    PENDIENTE,
    PAGADO,
    PARCIALMENTE_REEMBOLSADO,
    REEMBOLSADO,
    ANULADO;

    /**
     * ORD-005: PENDIENTE → PAGADO o ANULADO; PAGADO → parcial o total; parcial → parcial (reembolsos sucesivos) o
     * total. REEMBOLSADO y ANULADO son finales.
     */
    public boolean puedeTransicionarA(EstadoPago destino) {
        return switch (this) {
            case PENDIENTE -> destino == PAGADO || destino == ANULADO;
            case PAGADO -> destino == PARCIALMENTE_REEMBOLSADO || destino == REEMBOLSADO;
            case PARCIALMENTE_REEMBOLSADO -> destino == PARCIALMENTE_REEMBOLSADO || destino == REEMBOLSADO;
            case REEMBOLSADO, ANULADO -> false;
        };
    }

    /** Pagos que ya tienen dinero cobrado: habilitan la preparación y el cierre (ORD-004, ORD-006). */
    public boolean estaCobrado() {
        return this == PAGADO || this == PARCIALMENTE_REEMBOLSADO || this == REEMBOLSADO;
    }
}
