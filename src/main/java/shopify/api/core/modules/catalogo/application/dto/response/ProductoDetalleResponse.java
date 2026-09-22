package shopify.api.core.modules.catalogo.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ProductoDetalleResponse {
    private UUID id;
    private UUID tiendaId;
    private String titulo;
    private String slug;
    private String descripcion;
    private String proveedor;
    private Boolean esDigital;
    private Boolean estaPublicado;
    private Instant fechaCreacion;
    private List<VarianteResponse> variantes;
    private List<String> imagenes;

    @Getter
    @Builder
    public static class VarianteResponse {
        private UUID id;
        private String titulo;
        private String sku;
        private BigDecimal precio;
        private BigDecimal precioComparacion;
        private Integer stockDisponible;
        private Boolean permitirVentaSinStock;
    }
}