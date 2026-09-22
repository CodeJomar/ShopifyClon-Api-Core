package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Productos\"")
public class ProductoEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"Titulo\"", nullable = false, length = 200)
    private String titulo;

    @Column(name = "\"IdentificadorUrl\"", nullable = false, length = 200)
    private String identificadorUrl;

    @Column(name = "\"Descripcion\"", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "\"Proveedor\"", length = 100)
    private String proveedor;

    @Column(name = "\"EsDigital\"", nullable = false)
    private Boolean esDigital = false;

    @Column(name = "\"EstaPublicado\"", nullable = false)
    private Boolean estaPublicado = false;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VarianteProductoEntidad> variantes = new ArrayList<>();

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ImagenProductoEntidad> imagenes = new ArrayList<>();
}