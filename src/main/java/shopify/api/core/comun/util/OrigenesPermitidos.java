package shopify.api.core.comun.util;

import java.util.Arrays;
import java.util.List;

/** Parsea listas separadas por coma de orígenes permitidos en CORS y WebSocket (nunca {@code "*"}). */
public final class OrigenesPermitidos {

    private OrigenesPermitidos() {
    }

    public static List<String> desdeCsv(String origenes) {
        if (origenes == null || origenes.isBlank()) {
            return List.of();
        }
        List<String> lista = Arrays.stream(origenes.split(","))
                .map(String::strip)
                .filter(origen -> !origen.isEmpty())
                .distinct()
                .toList();
        if (lista.contains("*")) {
            throw new IllegalArgumentException("No se permite '*' en app.cors.origenes-permitidos");
        }
        return lista;
    }
}
