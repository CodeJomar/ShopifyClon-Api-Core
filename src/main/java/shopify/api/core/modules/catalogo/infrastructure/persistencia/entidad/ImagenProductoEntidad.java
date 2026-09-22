package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ImagenesProducto\"")
public class ImagenProductoEntidad extends AuditoriaEntidad {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ProductoId\"", nullable = false)
    private ProductoEntidad producto;

    @Column(name = "\"VarianteId\"")
    private UUID varianteId;

    @Column(name = "\"UrlImagen\"", nullable = false, columnDefinition = "TEXT")
    private String urlImagen;

    @Column(name = "\"TextoAlternativo\"", length = 200)
    private String textoAlternativo;

    @Column(name = "\"Posicion\"", nullable = false)
    private Integer posicion = 1;

    @Column(name = "\"EsPrincipal\"", nullable = false)
    private Boolean esPrincipal = false;
}