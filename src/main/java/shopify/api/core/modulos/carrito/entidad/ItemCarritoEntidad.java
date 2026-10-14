package shopify.api.core.modulos.carrito.entidad;

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
@Table(name = "\"ItemsCarrito\"")
public class ItemCarritoEntidad extends AuditoriaEntidad {

    @Column(name = "\"CarritoId\"", nullable = false)
    private UUID carritoId;

    @Column(name = "\"VarianteId\"", nullable = false)
    private UUID varianteId;

    /** 1 a 99 (CAR-003). */
    @Column(name = "\"Cantidad\"", nullable = false)
    private Integer cantidad;
}
