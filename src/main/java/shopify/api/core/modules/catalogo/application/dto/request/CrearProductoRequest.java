package shopify.api.core.modules.catalogo.application.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import shopify.api.core.shared.dto.BaseInputDto;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CrearProductoRequest extends BaseInputDto {

    @NotBlank(message = "El título del producto es obligatorio")
    @Size(max = 200, message = "El título no puede superar los 200 caracteres")
    private String titulo;

    private String descripcion;

    @Size(max = 100, message = "El proveedor no puede superar los 100 caracteres")
    private String proveedor;

    private Boolean esDigital = false;
    private Boolean estaPublicado = true;

    // Relación opcional con categoría
    private UUID categoriaId;

    // Datos de la variante por defecto
    @NotBlank(message = "El SKU es obligatorio")
    @Size(max = 80, message = "El SKU no puede superar los 80 caracteres")
    private String sku;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio no puede ser negativo")
    private BigDecimal precio;

    private BigDecimal precioComparacion;
    private BigDecimal costoUnitario;
    private BigDecimal pesoGramos = BigDecimal.ZERO;
    private Boolean requiereEnvio = true;

    // Inventario inicial
    @NotNull(message = "La cantidad disponible es obligatoria")
    @Min(value = 0, message = "El inventario inicial no puede ser negativo")
    private Integer cantidadDisponible;

    private Boolean permitirVentaSinStock = false;

    // Foto inicial
    private String urlImagenPrincipal;
}