package shopify.api.core.modulos.usuario.entidad;

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
@Table(name = "\"DireccionesUsuario\"")
public class DireccionUsuarioEntidad extends AuditoriaEntidad {

    @Column(name = "\"UsuarioId\"", nullable = false)
    private UUID usuarioId;

    @Column(name = "\"NombreContacto\"", nullable = false, length = 150)
    private String nombreContacto;

    @Column(name = "\"Telefono\"", length = 30)
    private String telefono;

    @Column(name = "\"DireccionLinea1\"", nullable = false, length = 255)
    private String direccionLinea1;

    @Column(name = "\"DireccionLinea2\"", length = 255)
    private String direccionLinea2;

    @Column(name = "\"Ciudad\"", nullable = false, length = 100)
    private String ciudad;

    @Column(name = "\"EstadoProvincia\"", nullable = false, length = 100)
    private String estadoProvincia;

    @Column(name = "\"CodigoPostal\"", nullable = false, length = 20)
    private String codigoPostal;

    @Column(name = "\"Pais\"", nullable = false, length = 100)
    private String pais;

    @Column(name = "\"EsPrincipal\"", nullable = false)
    private Boolean esPrincipal = false;
}
