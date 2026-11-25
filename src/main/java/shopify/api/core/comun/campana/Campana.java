package shopify.api.core.comun.campana;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Campañas comerciales fijas de la plataforma. Son constantes a propósito: el administrador solo elige cuál está
 * vigente (o ninguna); nombres y tramos de descuento no se editan por la API.
 */
public enum Campana {
    DIA_DE_LA_MADRE("Día de la Madre"),
    DIA_DEL_PADRE("Día del Padre"),
    HALLOWEEN("Halloween"),
    NAVIDAD("Navidad"),
    BLACK_FRIDAY("Black Friday");

    private final String nombre;

    Campana(String nombre) {
        this.nombre = nombre;
    }

    public String nombre() {
        return nombre;
    }

    /** La campaña con ese código (sin distinguir mayúsculas), o vacío si no existe. */
    public static Optional<Campana> de(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        String buscado = codigo.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(c -> c.name().equals(buscado)).findFirst();
    }
}
