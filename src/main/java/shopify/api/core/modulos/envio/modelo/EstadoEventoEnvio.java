package shopify.api.core.modulos.envio.modelo;

/**
 * Estado que registra un evento del historial del envío (CHECK {@code ChkEventosEnvioEstado}). Los cuatro primeros
 * coinciden con {@link EstadoEnvio}; {@link #NO_ENTREGADO} solo deja constancia y no cambia el estado del envío.
 */
public enum EstadoEventoEnvio {
    CREADO,
    EN_TRANSITO,
    ENTREGADO,
    NO_ENTREGADO,
    DEVUELTO
}
