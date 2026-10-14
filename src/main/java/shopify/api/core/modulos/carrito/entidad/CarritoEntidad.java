package shopify.api.core.modulos.carrito.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.carrito.modelo.EstadoCarrito;

@Getter
@Setter
@Entity
@Table(name = "\"Carritos\"")
public class CarritoEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    /** Opcional: el carrito de un invitado no tiene usuario hasta que inicia sesión (D-019). */
    @Column(name = "\"UsuarioId\"")
    private UUID usuarioId;

    /** Generado por el servidor (CAR-001); lo guarda el cliente en {@code X-Carrito-Token}. */
    @Column(name = "\"TokenSesion\"", nullable = false, length = 100)
    private String tokenSesion;

    @Column(name = "\"CuponId\"")
    private UUID cuponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Estado\"", nullable = false, length = 20)
    private EstadoCarrito estado = EstadoCarrito.ACTIVO;

    @Column(name = "\"OrdenId\"")
    private UUID ordenId;
}
