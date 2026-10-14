package shopify.api.core.modulos.orden.entidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import shopify.api.core.comun.auditoria.AuditoriaEntidad;

/**
 * Línea de una orden (tabla {@code ItemsOrden}). Inmutable tras crearse: no tiene setters, y el importe de la línea
 * lo valida el CHECK {@code ChkItemsOrdenTotalLinea} (precio × cantidad).
 */
@Getter
@Entity
@Table(name = "\"ItemsOrden\"")
public class ItemOrdenEntidad extends AuditoriaEntidad {

    @Column(name = "\"OrdenId\"", nullable = false)
    private UUID ordenId;

    @Column(name = "\"VarianteId\"", nullable = false)
    private UUID varianteId;

    /** Instantánea: aunque el producto se borre, la orden conserva el título. */
    @Column(name = "\"TituloProducto\"", nullable = false, length = 200)
    private String tituloProducto;

    @Column(name = "\"TituloVariante\"", nullable = false, length = 150)
    private String tituloVariante;

    @Column(name = "\"SKU\"", length = 80)
    private String sku;

    @Column(name = "\"Cantidad\"", nullable = false)
    private Integer cantidad;

    @Column(name = "\"PrecioUnitario\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "\"TotalLinea\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLinea;

    @Column(name = "\"RequiereEnvio\"", nullable = false)
    private Boolean requiereEnvio;

    protected ItemOrdenEntidad() {
    }

    public ItemOrdenEntidad(UUID ordenId, UUID varianteId, String tituloProducto, String tituloVariante, String sku,
                            int cantidad, BigDecimal precioUnitario, BigDecimal totalLinea, boolean requiereEnvio) {
        this.ordenId = ordenId;
        this.varianteId = varianteId;
        this.tituloProducto = tituloProducto;
        this.tituloVariante = tituloVariante;
        this.sku = sku;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.totalLinea = totalLinea;
        this.requiereEnvio = requiereEnvio;
    }
}
