package shopify.api.core.modulos.usuario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"Usuarios\"")
public class UsuarioEntidad extends AuditoriaEntidad {

    @Column(name = "\"CorreoElectronico\"", nullable = false, length = 255)
    private String correoElectronico;

    @Column(name = "\"ContrasenaHash\"", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "\"Nombres\"", nullable = false, length = 100)
    private String nombres;

    @Column(name = "\"Apellidos\"", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "\"Telefono\"", length = 30)
    private String telefono;

    @Column(name = "\"EstaActivo\"", nullable = false)
    private Boolean estaActivo = false;

    /** Solo se cambia en base de datos; ningún DTO ni servicio lo escribe. */
    @Column(name = "\"EsAdministradorPlataforma\"", nullable = false, updatable = false)
    private Boolean esAdministradorPlataforma = false;
}
