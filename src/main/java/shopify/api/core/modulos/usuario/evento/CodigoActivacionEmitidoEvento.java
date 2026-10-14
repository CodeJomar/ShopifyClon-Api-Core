package shopify.api.core.modulos.usuario.evento;

/** Se emite al registrarse y al reenviar la activación; el código viaja solo hacia el correo. */
public record CodigoActivacionEmitidoEvento(String correo, String nombres, String codigo) {

    @Override
    public String toString() {
        return "CodigoActivacionEmitidoEvento[correo=" + correo + "]";
    }
}
