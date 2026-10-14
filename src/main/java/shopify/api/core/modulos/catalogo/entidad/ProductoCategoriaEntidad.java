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
@Table(name = "\"ProductosCategorias\"")
public class ProductoCategoriaEntidad extends AuditoriaEntidad {

    @Column(name = "\"ProductoId\"", nullable = false)
    private UUID productoId;

    @Column(name = "\"CategoriaId\"", nullable = false)
    private UUID categoriaId;
}
