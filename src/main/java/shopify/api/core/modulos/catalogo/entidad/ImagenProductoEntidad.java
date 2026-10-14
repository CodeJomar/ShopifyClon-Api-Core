package shopify.api.core.modulos.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"ImagenesProducto\"")
public class ImagenProductoEntidad extends AuditoriaEntidad {

    @Column(name = "\"ProductoId\"", nullable = false)
    private UUID productoId;

    /** Opcional: la imagen corresponde a una variante del mismo producto. */
    @Column(name = "\"VarianteId\"")
    private UUID varianteId;

    @Column(name = "\"UrlImagen\"", nullable = false)
    private String urlImagen;

    @Column(name = "\"TextoAlternativo\"", length = 200)
    private String textoAlternativo;

    @Column(name = "\"Posicion\"", nullable = false)
    private Integer posicion = 1;

    @Column(name = "\"EsPrincipal\"", nullable = false)
    private Boolean esPrincipal = false;
}
