package shopify.api.core.modulos.tienda.dto.response;

/** Apariencia de la vitrina de una tienda; siempre completa (con los valores por defecto si no la cambió). */
public record AparienciaResponse(String paleta, String tipografia, String tamanoLetra, String diseno, String modo,
                                 String logoUrl) {
}
