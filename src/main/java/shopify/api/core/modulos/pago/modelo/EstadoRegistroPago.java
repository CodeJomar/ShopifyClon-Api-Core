package shopify.api.core.modulos.pago.modelo;

/** Estado de un registro de pago (CHECK {@code ChkPagosEstado}). Distinto de {@code EstadoPago} de la orden. */
public enum EstadoRegistroPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
