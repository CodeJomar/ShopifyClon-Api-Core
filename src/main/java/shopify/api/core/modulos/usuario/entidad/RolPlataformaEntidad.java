package shopify.api.core.modulos.usuario.entidad;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Rol de plataforma (B9): un nombre y un conjunto de permisos del catálogo {@code PermisoPlataforma}. */
@Getter
@Setter
@Entity
@Table(name = "\"RolesPlataforma\"")
public class RolPlataformaEntidad extends AuditoriaEntidad {

    @Column(name = "\"Codigo\"", nullable = false, length = 40)
    private String codigo;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    @Column(name = "\"Descripcion\"", length = 255)
    private String descripcion;

    /** Los roles de sistema no se editan ni se eliminan. */
    @Column(name = "\"EsSistema\"", nullable = false)
    private Boolean esSistema = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "\"RolesPlataformaPermisos\"", joinColumns = @JoinColumn(name = "\"RolId\""))
    @Column(name = "\"Permiso\"", length = 60)
    private Set<String> permisos = new HashSet<>();
}
