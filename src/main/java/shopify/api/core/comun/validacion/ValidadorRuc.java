package shopify.api.core.comun.validacion;

import java.util.Set;

/**
 * RUC peruano (R-030): 11 dígitos, empieza con 10, 15, 17 o 20 y el último es el dígito verificador de SUNAT
 * (módulo 11 con los pesos 5, 4, 3, 2, 7, 6, 5, 4, 3, 2 sobre los diez primeros). Cálculo puro, sin Spring.
 */
public final class ValidadorRuc {

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final Set<String> PREFIJOS = Set.of("10", "15", "17", "20");

    private ValidadorRuc() {
    }

    /** Quita espacios y guiones («20 1000-70970» → «20100070970»); vacío o nulo devuelve nulo. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.replaceAll("[\\s\\-]", "");
        return limpio.isEmpty() ? null : limpio;
    }

    /** {@code true} si el texto (ya normalizado o no) es un RUC con formato, prefijo y dígito verificador correctos. */
    public static boolean esValido(String texto) {
        String ruc = normalizar(texto);
        if (ruc == null || !ruc.matches("\\d{11}") || !PREFIJOS.contains(ruc.substring(0, 2))) {
            return false;
        }
        return digitoVerificador(ruc.substring(0, 10)) == ruc.charAt(10) - '0';
    }

    /** Dígito verificador que corresponde a los diez primeros dígitos. */
    public static int digitoVerificador(String primerosDiez) {
        int suma = 0;
        for (int i = 0; i < PESOS.length; i++) {
            suma += (primerosDiez.charAt(i) - '0') * PESOS[i];
        }
        int digito = 11 - (suma % 11);
        return digito == 10 ? 0 : digito == 11 ? 1 : digito;
    }
}
