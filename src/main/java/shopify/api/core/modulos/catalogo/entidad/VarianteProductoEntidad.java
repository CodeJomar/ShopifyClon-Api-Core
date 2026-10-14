package shopify.api.core.modulos.catalogo.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

@Getter
@Setter
@Entity
@Table(name = "\"VariantesProducto\"")
public class VarianteProductoEntidad extends AuditoriaEntidad {

    @Column(name = "\"ProductoId\"", nullable = false)
    private UUID productoId;

    @Column(name = "\"Titulo\"", nullable = false, length = 150)
    private String titulo;

    /** En mayúsculas y sin espacios; único entre variantes de la misma tienda (CAT-003, D-013). */
    @Column(name = "\"SKU\"", nullable = false, length = 80)
    private String sku;

    @Column(name = "\"CodigoBarras\"", length = 50)
    private String codigoBarras;

    @Column(name = "\"Precio\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "\"PrecioComparacion\"", precision = 12, scale = 2)
    private BigDecimal precioComparacion;

    /** Dato interno: nunca sale por la vitrina (CAT-011, CAT-012). */
    @Column(name = "\"CostoUnitario\"", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "\"PesoGramos\"", precision = 10, scale = 2)
    private BigDecimal pesoGramos = BigDecimal.ZERO;

    @Column(name = "\"RequiereEnvio\"", nullable = false)
    private Boolean requiereEnvio = true;
}
