package shopify.api.core.modulos.suscripcion.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"PlanesSuscripcion\"")
public class PlanSuscripcionEntidad extends AuditoriaEntidad {

    @Column(name = "\"Codigo\"", nullable = false, length = 30)
    private String codigo;

    @Column(name = "\"Nombre\"", nullable = false, length = 100)
    private String nombre;

    @Column(name = "\"Descripcion\"", length = 500)
    private String descripcion;

    @Column(name = "\"PrecioMensual\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioMensual;

    @Column(name = "\"PrecioAnual\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnual;

    @Column(name = "\"Moneda\"", nullable = false, length = 3)
    private String moneda;

    /** NULL = ilimitado. */
    @Column(name = "\"LimiteProductos\"")
    private Integer limiteProductos;

    /** Sin contar al propietario. */
    @Column(name = "\"LimitePersonal\"", nullable = false)
    private Integer limitePersonal;

    @Column(name = "\"PorcentajeComision\"", nullable = false, precision = 5, scale = 4)
    private BigDecimal porcentajeComision;

    @Column(name = "\"DiasPrueba\"", nullable = false)
    private Integer diasPrueba = 14;

    /** FALSE = no se ofrece a tiendas nuevas (SUS-013). */
    @Column(name = "\"EsPublico\"", nullable = false)
    private Boolean esPublico = true;

    @Column(name = "\"Orden\"", nullable = false)
    private Integer orden = 0;
}
