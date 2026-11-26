package shopify.api.core.modulos.tienda.dto.response;

import java.util.List;

/** Códigos válidos de cada opción de apariencia y los valores por defecto. */
public record OpcionesAparienciaResponse(Opciones opciones, PorDefecto porDefecto) {

    public record Opciones(List<String> paleta, List<String> tipografia, List<String> tamanoLetra, List<String> diseno,
                           List<String> modo) {
    }

    public record PorDefecto(String paleta, String tipografia, String tamanoLetra, String diseno, String modo) {
    }
}
