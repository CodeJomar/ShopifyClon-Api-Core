package shopify.api.core.modulos.tienda.modelo;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import shopify.api.core.comun.excepcion.ReglaNegocioException;

/** Normaliza y valida el subdominio de una tienda (TIE-001). La unicidad la comprueba el servicio. */
public final class ValidadorSubdominio {

    public static final int LONGITUD_MINIMA = 3;
    public static final int LONGITUD_MAXIMA = 63;

    private static final Pattern FORMATO = Pattern.compile("^[a-z0-9](-?[a-z0-9])*$");
    private static final Set<String> RESERVADOS = Set.of(
            "www", "api", "admin", "app", "panel", "vitrina", "auth", "cuenta", "plataforma", "soporte", "mail");

    private ValidadorSubdominio() {
    }

    /** Devuelve el subdominio limpio (sin espacios en los extremos y en minúsculas) o lanza {@code TIE-001}. */
    public static String validar(String texto) {
        String subdominio = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);

        if (subdominio.length() < LONGITUD_MINIMA || subdominio.length() > LONGITUD_MAXIMA) {
            throw invalido("El subdominio debe tener entre 3 y 63 caracteres");
        }
        if (!FORMATO.matcher(subdominio).matches()) {
            throw invalido("Usa solo letras minúsculas, números y guiones; no puede empezar ni terminar con guion");
        }
        if (RESERVADOS.contains(subdominio)) {
            throw invalido("Ese subdominio está reservado");
        }
        return subdominio;
    }

    public static ReglaNegocioException enUso() {
        return invalido("Ese subdominio ya está en uso");
    }

    private static ReglaNegocioException invalido(String motivo) {
        return new ReglaNegocioException("TIE-001", motivo);
    }
}
