package shopify.api.core.comun.util;

/** Resumen de una descripción para tarjetas y listados: texto plano de hasta {@value #MAXIMO} caracteres. */
public final class DescripcionCorta {

    public static final int MAXIMO = 110;

    private DescripcionCorta() {
    }

    /** Quita etiquetas HTML y espacios repetidos; si pasa del máximo, corta con «…». Nulo si no queda texto. */
    public static String de(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String plano = descripcion.replaceAll("<[^>]*>", " ").replaceAll("\s+", " ").strip();
        if (plano.isEmpty()) {
            return null;
        }
        if (plano.length() <= MAXIMO) {
            return plano;
        }
        return plano.substring(0, MAXIMO - 1).stripTrailing() + "…";
    }
}
