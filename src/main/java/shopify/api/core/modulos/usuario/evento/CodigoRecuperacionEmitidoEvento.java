package shopify.api.core.modulos.usuario.evento;

/** Se emite al solicitar recuperar la clave; el código viaja solo hacia el correo. */
public record CodigoRecuperacionEmitidoEvento(String correo, String nombres, String codigo) {

    @Override
    public String toString() {
        return "CodigoRecuperacionEmitidoEvento[correo=" + correo + "]";
    }
}
