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
@Table(name = "\"Productos\"")
public class ProductoEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"Titulo\"", nullable = false, length = 200)
    private String titulo;

    @Column(name = "\"IdentificadorUrl\"", nullable = false, length = 200)
    private String identificadorUrl;

    @Column(name = "\"Descripcion\"")
    private String descripcion;

    @Column(name = "\"Proveedor\"", length = 100)
    private String proveedor;

    @Column(name = "\"EsDigital\"", nullable = false)
    private Boolean esDigital = false;

    /** Nace no publicado (CAT-007). */
    @Column(name = "\"EstaPublicado\"", nullable = false)
    private Boolean estaPublicado = false;
}
