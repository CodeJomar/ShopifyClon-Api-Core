package shopify.api.core.modulos.catalogo.servicio;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import shopify.api.core.comun.excepcion.ReglaNegocioException;
import shopify.api.core.comun.util.GeneradorSlug;

/** Slugs de productos y categorías (CAT-002): generados desde el nombre o validados si la persona los escribe. */
final class IdentificadoresUrl {

    /** Espacio reservado para el sufijo "-NN" de los duplicados. */
    private static final int RESERVA_SUFIJO = 6;
    private static final Pattern FORMATO = Pattern.compile("^[a-z0-9](-?[a-z0-9])*$");

    private IdentificadoresUrl() {
    }

    /** Del texto libre; si ya existe, añade {@code -2}, {@code -3}… (CAT-002). */
    static String generar(String texto, int maxLongitud, Predicate<String> ocupado) {
        String raiz = GeneradorSlug.generar(texto, maxLongitud - RESERVA_SUFIJO);
        String candidato = raiz;
        int sufijo = 2;
        while (ocupado.test(candidato)) {
            candidato = raiz + "-" + sufijo;
            sufijo++;
        }
        return candidato;
    }

    /** Un identificador escrito a mano debe tener formato válido y no estar en uso; no se le añade sufijo. */
    static String validar(String texto, int maxLongitud, Predicate<String> ocupado) {
        String slug = texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
        if (slug.isEmpty() || slug.length() > maxLongitud || !FORMATO.matcher(slug).matches()) {
            throw new ReglaNegocioException("CAT-002",
                    "El identificador solo puede tener minúsculas, números y guiones, sin empezar ni terminar con guion");
        }
        if (ocupado.test(slug)) {
            throw new ReglaNegocioException("CAT-002", "Ese identificador ya está en uso en tu tienda");
        }
        return slug;
    }
}
