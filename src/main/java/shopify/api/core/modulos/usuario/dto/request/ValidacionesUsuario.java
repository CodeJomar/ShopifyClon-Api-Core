package shopify.api.core.modulos.usuario.dto.request;

/** Reglas de validación compartidas por los requests del módulo (USR-002). */
final class ValidacionesUsuario {

    static final String PATRON_CONTRASENA = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    static final String MENSAJE_CONTRASENA =
            "La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número";
    static final String PATRON_TELEFONO = "^[0-9+ ()-]{6,30}$";
    static final String MENSAJE_TELEFONO = "El teléfono solo puede tener números, espacios y los símbolos + ( ) -";
    static final String PATRON_CODIGO = "^\\d{6}$";
    static final String MENSAJE_CODIGO = "El código debe tener 6 dígitos";

    private ValidacionesUsuario() {
    }
}
