package shopify.api.core.modulos.tienda.dto.request;

/**
 * Validación común de los requests de personal. {@code PROPIETARIO} pasa el patrón a propósito:
 * el servicio lo rechaza con {@code PER-003}, que es el código de negocio que le corresponde.
 */
final class ReglasPersonal {

    static final String PATRON_ROL = "^(PROPIETARIO|ADMINISTRADOR|GESTOR_CATALOGO|GESTOR_PEDIDOS)$";
    static final String MENSAJE_ROL = "El rol debe ser ADMINISTRADOR, GESTOR_CATALOGO o GESTOR_PEDIDOS";
    static final String MENSAJE_MOTIVO = "El motivo debe tener entre 5 y 255 caracteres";

    private ReglasPersonal() {
    }
}
