package shopify.api.core.modules.usuario.infrastructure.persistencia.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"Roles\"")
public class RolEntidad extends AuditoriaEntidad {

    @Column(name = "\"Nombre\"", nullable = false, length = 50)
    private String nombre;

    @Column(name = "\"Codigo\"", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "\"Descripcion\"", length = 255)
    private String descripcion;
}