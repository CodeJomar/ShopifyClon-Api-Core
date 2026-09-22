package shopify.api.core.modules.catalogo.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ProductoResumenResponse {
    private UUID id;
    private String titulo;
    private String slug;
    private String proveedor;
    private Boolean estaPublicado;
    private BigDecimal precioDesde;
    private Integer stockTotal;
    private String imagenPrincipal;
    private Instant fechaCreacion;
}