package shopify.api.core.modulos.usuario.evento;

/** A-007: segundo paso del login; el código viaja solo hacia el correo. */
public record CodigoSegundoFactorEmitidoEvento(String correo, String nombres, String codigo) {

    @Override
    public String toString() {
        return "CodigoSegundoFactorEmitidoEvento[correo=" + correo + "]";
    }
}
