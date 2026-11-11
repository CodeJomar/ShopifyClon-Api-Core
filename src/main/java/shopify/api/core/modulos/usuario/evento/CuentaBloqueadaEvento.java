package shopify.api.core.modulos.usuario.evento;

/** A-007: la cuenta se bloqueó por intentos fallidos; el código de desbloqueo viaja solo hacia el correo. */
public record CuentaBloqueadaEvento(String correo, String nombres, String codigo, long minutosBloqueo) {

    @Override
    public String toString() {
        return "CuentaBloqueadaEvento[correo=" + correo + "]";
    }
}
