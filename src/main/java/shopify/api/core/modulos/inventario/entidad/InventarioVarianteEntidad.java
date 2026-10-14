package shopify.api.core.modulos.inventario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/** Cantidades de una variante. Solo las cambia el módulo inventario (INV-008). */
@Getter
@Setter
@Entity
@Table(name = "\"InventarioVariante\"")
public class InventarioVarianteEntidad extends AuditoriaEntidad {

    @Column(name = "\"VarianteId\"", nullable = false)
    private UUID varianteId;

    @Column(name = "\"CantidadDisponible\"", nullable = false)
    private Integer cantidadDisponible = 0;

    @Column(name = "\"CantidadReservada\"", nullable = false)
    private Integer cantidadReservada = 0;

    @Column(name = "\"RastreaInventario\"", nullable = false)
    private Boolean rastreaInventario = true;

    @Column(name = "\"PermitirVentaSinStock\"", nullable = false)
    private Boolean permitirVentaSinStock = false;
}
