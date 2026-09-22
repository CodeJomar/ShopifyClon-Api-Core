package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.util.UUID;

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