package shopify.api.core.modulos.tienda.modelo;

import java.util.List;

/**
 * Lista blanca de la apariencia de la vitrina (R-031). Los códigos son fijos; el frontend los traduce a colores,
 * fuentes y diseños. El primero de cada lista es el valor por defecto. Los patrones de validación (constantes para
 * poder usarlos en anotaciones) deben coincidir con las listas: lo comprueba una prueba.
 */
public final class OpcionesApariencia {

    public static final List<String> PALETA = List.of("OTEMU", "OCEANO", "ATARDECER", "BOSQUE", "GRAFITO", "ROSA");
    public static final List<String> TIPOGRAFIA = List.of("MODERNA", "CLASICA", "REDONDEADA", "COMPACTA");
    public static final List<String> TAMANO_LETRA = List.of("NORMAL", "GRANDE", "MUY_GRANDE");
    public static final List<String> DISENO = List.of("CUADRICULA", "REVISTA", "LISTA", "PORTADA");
    public static final List<String> MODO = List.of("OSCURO", "CLARO", "ALTO_CONTRASTE");

    public static final String PATRON_PALETA = "^(OTEMU|OCEANO|ATARDECER|BOSQUE|GRAFITO|ROSA)$";
    public static final String PATRON_TIPOGRAFIA = "^(MODERNA|CLASICA|REDONDEADA|COMPACTA)$";
    public static final String PATRON_TAMANO_LETRA = "^(NORMAL|GRANDE|MUY_GRANDE)$";
    public static final String PATRON_DISENO = "^(CUADRICULA|REVISTA|LISTA|PORTADA)$";
    public static final String PATRON_MODO = "^(OSCURO|CLARO|ALTO_CONTRASTE)$";

    private OpcionesApariencia() {
    }

    public static String porDefecto(List<String> opciones) {
        return opciones.get(0);
    }
}
