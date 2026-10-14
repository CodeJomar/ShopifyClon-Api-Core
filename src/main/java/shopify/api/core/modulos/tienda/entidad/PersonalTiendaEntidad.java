package shopify.api.core.modulos.tienda.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Pertenencia de un usuario al personal de una tienda, con su rol. Sin {@code @ManyToOne}: referencias por id. */
@Getter
@Setter
@Entity
@Table(name = "\"PersonalTienda\"")
public class PersonalTiendaEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"UsuarioId\"", nullable = false)
    private UUID usuarioId;

    @Column(name = "\"RolId\"", nullable = false)
    private UUID rolId;
}
