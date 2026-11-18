package shopify.api.core.modulos.resena.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Reseña de un producto (R-011): una por persona y producto, con calificación de 1 a 5 y comentario opcional. */
@Getter
@Setter
@Entity
@Table(name = "\"ResenasProducto\"")
public class ResenaEntidad extends AuditoriaEntidad {

    @Column(name = "\"ProductoId\"", nullable = false)
    private UUID productoId;

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"UsuarioId\"", nullable = false)
    private UUID usuarioId;

    @Column(name = "\"Calificacion\"", nullable = false)
    private Integer calificacion;

    @Column(name = "\"Comentario\"", length = 1000)
    private String comentario;

    /** Moderación de plataforma: una reseña oculta no se muestra ni cuenta en el promedio. */
    @Column(name = "\"Oculta\"", nullable = false)
    private Boolean oculta = false;

    @Column(name = "\"MotivoOcultacion\"", length = 255)
    private String motivoOcultacion;
}
