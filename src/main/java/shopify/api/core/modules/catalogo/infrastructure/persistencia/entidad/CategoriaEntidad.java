package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Categorias\"")
public class CategoriaEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    // En tu DDL la columna se llama "IdentificadorUrl"
    @Column(name = "\"IdentificadorUrl\"", nullable = false, length = 100)
    private String slug;

    @Column(name = "\"Descripcion\"", columnDefinition = "TEXT")
    private String descripcion;
}