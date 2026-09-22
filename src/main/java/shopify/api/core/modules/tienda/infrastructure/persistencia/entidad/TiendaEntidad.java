package shopify.api.core.modules.tienda.infrastructure.persistencia.entidad;

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
@Table(name = "\"Tiendas\"")
public class TiendaEntidad extends AuditoriaEntidad {

    @Column(name = "\"PropietarioId\"", nullable = false)
    private UUID propietarioId;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    @Column(name = "\"Subdominio\"", nullable = false, unique = true, length = 100)
    private String subdominio;

    @Column(name = "\"DominioPersonalizado\"", length = 150)
    private String dominioPersonalizado;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda = "USD";

    @Column(name = "\"CorreoContacto\"", nullable = false, length = 255)
    private String correoContacto;

    @Column(name = "\"EstaActiva\"", nullable = false)
    private Boolean estaActiva = true;
}