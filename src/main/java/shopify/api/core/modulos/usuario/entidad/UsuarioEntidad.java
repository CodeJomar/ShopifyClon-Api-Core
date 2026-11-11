package shopify.api.core.modulos.usuario.entidad;

import jakarta.persistence.Column;
import java.time.Instant;
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

    /** A-007: los JWT emitidos antes de este instante ya no valen (cambio de clave o cierre de sesiones). */
    @Column(name = "\"SesionesValidasDesde\"")
    private Instant sesionesValidasDesde;

    /** A-007: fallos de login seguidos; se reinicia al entrar bien o al bloquear. */
    @Column(name = "\"IntentosLoginFallidos\"", nullable = false)
    private Integer intentosLoginFallidos = 0;

    /** A-007: mientras sea futuro, el login se rechaza con USR-014. */
    @Column(name = "\"BloqueadoHasta\"")
    private Instant bloqueadoHasta;

    /** A-007: si es true, el login pide además un código enviado por correo. */
    @Column(name = "\"DosPasosActivo\"", nullable = false)
    private Boolean dosPasosActivo = false;
}
