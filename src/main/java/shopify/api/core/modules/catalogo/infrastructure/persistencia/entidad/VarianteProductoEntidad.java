package shopify.api.core.modules.catalogo.infrastructure.persistencia.entidad;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.auditoria.AuditoriaEntidad;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "\"VariantesProducto\"")
public class VarianteProductoEntidad extends AuditoriaEntidad {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ProductoId\"", nullable = false)
    private ProductoEntidad producto;

    @Column(name = "\"Titulo\"", nullable = false, length = 150)
    private String titulo;

    @Column(name = "\"SKU\"", nullable = false, length = 80)
    private String sku;

    @Column(name = "\"CodigoBarras\"", length = 50)
    private String codigoBarras;

    @Column(name = "\"Precio\"", nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "\"PrecioComparacion\"", precision = 12, scale = 2)
    private BigDecimal precioComparacion;

    @Column(name = "\"CostoUnitario\"", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    @Column(name = "\"PesoGramos\"", precision = 10, scale = 2)
    private BigDecimal pesoGramos = BigDecimal.ZERO;

    @Column(name = "\"RequiereEnvio\"", nullable = false)
    private Boolean requiereEnvio = true;

    @OneToOne(mappedBy = "variante", cascade = CascadeType.ALL, orphanRemoval = true)
    private InventarioVarianteEntidad inventario;
}