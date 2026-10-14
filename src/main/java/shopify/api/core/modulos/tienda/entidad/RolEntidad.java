package shopify.api.core.modulos.tienda.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Catálogo de roles de tienda; solo lectura en tiempo de ejecución. */
@Getter
@Setter
@Entity
@Table(name = "\"Roles\"")
public class RolEntidad extends AuditoriaEntidad {

    @Column(name = "\"Nombre\"", nullable = false, length = 50)
    private String nombre;

    @Column(name = "\"Codigo\"", nullable = false, length = 30)
    private String codigo;

    @Column(name = "\"Descripcion\"", length = 255)
    private String descripcion;
}
