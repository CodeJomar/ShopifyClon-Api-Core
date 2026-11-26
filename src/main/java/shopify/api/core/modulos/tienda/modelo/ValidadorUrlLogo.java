package shopify.api.core.modulos.tienda.modelo;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL del logo de una tienda (R-031): puede ser una imagen subida a la API o cualquier URL externa, siempre que sea
 * {@code https} con servidor, de hasta 500 caracteres y sin espacios ni caracteres de control ni comillas o signos
 * de menor y mayor. Rechaza {@code javascript:}, {@code data:}, {@code http:} y URL con usuario y clave incrustados.
 */
public final class ValidadorUrlLogo {

    public static final int LARGO_MAXIMO = 500;
    public static final String MENSAJE = "El logo debe ser una URL https válida (hasta 500 caracteres, sin espacios)";

    private ValidadorUrlLogo() {
    }

    public static boolean esValida(String url) {
        if (url == null || url.isEmpty() || url.length() > LARGO_MAXIMO) {
            return false;
        }
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c <= ' ' || c == 127 || c == '"' || c == '\'' || c == '<' || c == '>' || c == '\\' || Character.isWhitespace(c)) {
                return false;
            }
        }
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank() && uri.getUserInfo() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
