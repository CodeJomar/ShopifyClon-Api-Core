package shopify.api.core.modulos.catalogo.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Variante al crear un producto o al agregarla. Los campos de inventario crean su inventario inicial (INV-008). */
public record VarianteRequest(
        @Size(max = 150, message = "El título de la variante no puede superar los 150 caracteres")
        String titulo,

        @NotBlank(message = "El SKU es obligatorio")
        @Size(max = 80, message = "El SKU no puede superar los 80 caracteres")
        String sku,

        @Size(max = 50, message = "El código de barras no puede superar los 50 caracteres")
        String codigoBarras,

        @NotNull(message = "El precio es obligatorio")
        @DecimalMin(value = "0", message = "El precio no puede ser negativo")
        BigDecimal precio,

        @DecimalMin(value = "0", message = "El precio de comparación no puede ser negativo")
        BigDecimal precioComparacion,

        @DecimalMin(value = "0", message = "El costo unitario no puede ser negativo")
        BigDecimal costoUnitario,

        @DecimalMin(value = "0", message = "El peso no puede ser negativo")
        BigDecimal pesoGramos,

        Boolean requiereEnvio,

        @Min(value = 0, message = "La cantidad inicial no puede ser negativa")
        Integer cantidadInicial,

        Boolean rastreaInventario,

        Boolean permitirVentaSinStock) {
}
