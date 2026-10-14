package shopify.api.core.modulos.inventario.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.inventario.modelo.TipoMovimiento;

/** Libro de movimientos: solo se añaden filas (INV-008). */
@Getter
@Setter
@Entity
@Table(name = "\"MovimientosInventario\"")
public class MovimientoInventarioEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    @Column(name = "\"VarianteId\"", nullable = false)
    private UUID varianteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"Tipo\"", nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(name = "\"DeltaDisponible\"", nullable = false)
    private Integer deltaDisponible = 0;

    @Column(name = "\"DeltaReservado\"", nullable = false)
    private Integer deltaReservado = 0;

    @Column(name = "\"DisponibleResultante\"", nullable = false)
    private Integer disponibleResultante;

    @Column(name = "\"ReservadoResultante\"", nullable = false)
    private Integer reservadoResultante;

    @Column(name = "\"OrdenId\"")
    private UUID ordenId;

    @Column(name = "\"Motivo\"", length = 255)
    private String motivo;
}
