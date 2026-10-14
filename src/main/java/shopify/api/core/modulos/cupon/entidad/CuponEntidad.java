package shopify.api.core.modulos.cupon.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;
import shopify.api.core.modulos.cupon.modelo.TipoDescuento;

@Getter
@Setter
@Entity
@Table(name = "\"Cupones\"")
public class CuponEntidad extends AuditoriaEntidad {

    @Column(name = "\"TiendaId\"", nullable = false)
    private UUID tiendaId;

    /** Siempre en mayúsculas; la unicidad por tienda la garantiza {@code UxCuponesTiendaCodigo} (CUP-001). */
    @Column(name = "\"Codigo\"", nullable = false, length = 50)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"TipoDescuento\"", nullable = false, length = 20)
    private TipoDescuento tipoDescuento;

    @Column(name = "\"ValorDescuento\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorDescuento;

    @Column(name = "\"MontoMinimoCompra\"", precision = 12, scale = 2)
    private BigDecimal montoMinimoCompra = BigDecimal.ZERO;

    /** NULL = sin límite. */
    @Column(name = "\"LimiteUsosTotal\"")
    private Integer limiteUsosTotal;

    @Column(name = "\"CantidadUsosActual\"", nullable = false)
    private Integer cantidadUsosActual = 0;

    @Column(name = "\"FechaInicio\"", nullable = false)
    private Instant fechaInicio;

    @Column(name = "\"FechaFin\"")
    private Instant fechaFin;

    @Column(name = "\"EstaActivo\"", nullable = false)
    private Boolean estaActivo = true;
}
