package shopify.api.core.modulos.tienda.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import shopify.api.core.modulos.tienda.modelo.OpcionesApariencia;

/**
 * Apariencia completa de la vitrina: los cinco códigos son obligatorios (lista blanca). {@code logo_url}: ausente
 * conserva el logo actual, {@code null} (o vacío) lo quita, y un valor debe ser una imagen subida por esta tienda al
 * almacén de la API (se valida en el servicio). Es una clase y no un {@code record} para poder distinguir «ausente»
 * de «null»: el setter solo se llama cuando la clave viene en el JSON.
 */
public class ActualizarAparienciaRequest {

    @NotBlank(message = "La paleta es obligatoria")
    @Pattern(regexp = OpcionesApariencia.PATRON_PALETA, message = "La paleta no es válida (OTEMU, OCEANO, ATARDECER, BOSQUE, GRAFITO o ROSA)")
    private String paleta;

    @NotBlank(message = "La tipografía es obligatoria")
    @Pattern(regexp = OpcionesApariencia.PATRON_TIPOGRAFIA, message = "La tipografía no es válida (MODERNA, CLASICA, REDONDEADA o COMPACTA)")
    private String tipografia;

    @NotBlank(message = "El tamaño de letra es obligatorio")
    @Pattern(regexp = OpcionesApariencia.PATRON_TAMANO_LETRA, message = "El tamaño de letra no es válido (NORMAL, GRANDE o MUY_GRANDE)")
    private String tamanoLetra;

    @NotBlank(message = "El diseño es obligatorio")
    @Pattern(regexp = OpcionesApariencia.PATRON_DISENO, message = "El diseño no es válido (CUADRICULA, REVISTA, LISTA o PORTADA)")
    private String diseno;

    @NotBlank(message = "El modo es obligatorio")
    @Pattern(regexp = OpcionesApariencia.PATRON_MODO, message = "El modo no es válido (OSCURO, CLARO o ALTO_CONTRASTE)")
    private String modo;

    private String logoUrl;
    private boolean logoUrlPresente;

    public String paleta() {
        return paleta;
    }

    public String tipografia() {
        return tipografia;
    }

    public String tamanoLetra() {
        return tamanoLetra;
    }

    public String diseno() {
        return diseno;
    }

    public String modo() {
        return modo;
    }

    public String logoUrl() {
        return logoUrl;
    }

    /** {@code true} si el JSON trajo la clave {@code logo_url}, aunque fuera {@code null}. */
    public boolean logoUrlPresente() {
        return logoUrlPresente;
    }

    @JsonProperty("paleta")
    public void setPaleta(String paleta) {
        this.paleta = paleta;
    }

    @JsonProperty("tipografia")
    public void setTipografia(String tipografia) {
        this.tipografia = tipografia;
    }

    @JsonProperty("tamano_letra")
    public void setTamanoLetra(String tamanoLetra) {
        this.tamanoLetra = tamanoLetra;
    }

    @JsonProperty("diseno")
    public void setDiseno(String diseno) {
        this.diseno = diseno;
    }

    @JsonProperty("modo")
    public void setModo(String modo) {
        this.modo = modo;
    }

    @JsonProperty("logo_url")
    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
        this.logoUrlPresente = true;
    }
}
