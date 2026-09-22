package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"InventarioVariante\"")
public class InventarioVarianteEntidad extends AuditoriaEntidad {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"VarianteId\"", nullable = false, unique = true)
    private VarianteProductoEntidad variante;

    @Column(name = "\"CantidadDisponible\"", nullable = false)
    private Integer cantidadDisponible = 0;

    @Column(name = "\"CantidadReservada\"", nullable = false)
    private Integer cantidadReservada = 0;

    @Column(name = "\"RastreaInventario\"", nullable = false)
    private Boolean rastreaInventario = true;

    @Column(name = "\"PermitirVentaSinStock\"", nullable = false)
    private Boolean permitirVentaSinStock = false;
}