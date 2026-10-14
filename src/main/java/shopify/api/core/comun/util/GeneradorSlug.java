package shopify.api.core.comun.util;

import java.text.Normalizer;
import java.util.Locale;

/** Convierte texto libre en un identificador apto para URL. */
public final class GeneradorSlug {

    private static final String SLUG_VACIO = "item";

    private GeneradorSlug() {
    }

    public static String generar(String texto, int maxLongitud) {
        String base = texto == null ? "" : texto;
        String sinTildes = Normalizer.normalize(base.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = sinTildes
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > maxLongitud) {
            slug = slug.substring(0, Math.max(maxLongitud, 0)).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? SLUG_VACIO : slug;
    }
}
